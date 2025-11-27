package com.ssafy.keeping.domain.charge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.charge.canonical.CanonicalPrepayment;
import com.ssafy.keeping.domain.charge.dto.request.PrepaymentRequestDto;
import com.ssafy.keeping.domain.charge.dto.response.PrepaymentResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardPaymentResponseDto;
import com.ssafy.keeping.domain.charge.model.ChargeBonus;
import com.ssafy.keeping.domain.charge.service.ChargeBonusService;
import com.ssafy.keeping.domain.idempotency.constant.IdemActorType;
import com.ssafy.keeping.domain.idempotency.constant.IdemStatus;
import com.ssafy.keeping.domain.idempotency.dto.IdemBegin;
import com.ssafy.keeping.domain.idempotency.model.IdempotencyKey;
import com.ssafy.keeping.domain.idempotency.model.IdempotentResult;
import com.ssafy.keeping.domain.idempotency.repository.IdempotencyKeyRepository;
import com.ssafy.keeping.domain.idempotency.service.IdempotencyService;
import com.ssafy.keeping.domain.charge.model.SettlementTask;
import com.ssafy.keeping.domain.charge.repository.SettlementTaskRepository;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.payment.transactions.constant.TransactionType;
import com.ssafy.keeping.domain.store.model.Store;
import com.ssafy.keeping.domain.store.repository.StoreRepository;
import com.ssafy.keeping.domain.payment.transactions.model.Transaction;
import com.ssafy.keeping.domain.payment.transactions.repository.TransactionRepository;
import com.ssafy.keeping.domain.wallet.constant.LotSourceType;
import com.ssafy.keeping.domain.wallet.constant.WalletType;
import com.ssafy.keeping.domain.wallet.constant.LotStatus;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.model.WalletStoreBalance;
import com.ssafy.keeping.domain.wallet.model.WalletStoreLot;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreBalanceRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreLotRepository;
import com.ssafy.keeping.domain.event.service.KafkaEventProducer;
import com.ssafy.keeping.domain.event.dto.PaymentEvent;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PrepaymentService {

    private final SsafyFinanceApiService ssafyFinanceApiService;
    private final CustomerRepository customerRepository;
    private final StoreRepository storeRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletStoreLotRepository walletStoreLotRepository;
    private final WalletStoreBalanceRepository walletStoreBalanceRepository;
    private final SettlementTaskRepository settlementTaskRepository;
    private final KafkaEventProducer kafkaEventProducer;
    private final ChargeBonusService chargeBonusService;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final IdempotencyService idempotencyService;
    @Qualifier("canonicalObjectMapper")
    private final ObjectMapper canonicalObjectMapper;

    private final ObjectMapper objectMapper;

    // ObjectMapper : 자바객체 -> JSON / JSON -> 자바객체
    /**
     * 선결제 처리 (멱등성 적용)
     * - 멱등 스코프: (actorType=CUSTOMER, actorId=userId, path=/stores/{storeId}/prepayment, key=Idempotency-Key)
     * - 상태 흐름:
     *   DONE                           → 저장된 응답 재생(200 OK)
     *   IN_PROGRESS(타 프로세스 선점)     → 202 Accepted
     *   신규                            → 본 처리 수행 → DONE 기록 후 201 Created
     */
    public IdempotentResult<PrepaymentResponseDto> processPayment(Long storeId, Long customerId, String idempotencyKeyHeader, PrepaymentRequestDto requestDto) {
        // 입력 검증
        if (requestDto == null) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        // 멱등 바디 정규화 → SHA-256
        String canonicalBody = canonicalizeRequestBody(requestDto);
        byte[] bodyHash = IdempotencyService.sha256(canonicalBody); // 암호화
        // sha256 : 해시 암호화의 알고리즘

        // 멱등 선점 또는 로드
        UUID keyUuid = UUID.fromString(idempotencyKeyHeader);
        // 클라이언트가 보내준 텍스트(String) 형식의 키를, 서버가 사용하기 좋은 객체(UUID) 형식으로 변환
        String path = "/stores/" + storeId + "/prepayment";
        IdemBegin begin = idempotencyService.beginOrLoad(IdemActorType.CUSTOMER, customerId, "POST", path, keyUuid, bodyHash);
        // 이미 키가 있으면 기존 상태를 로드하고, 없으면 새로 생성해서 IN_PROGRESS로 설정
        IdempotencyKey slot = begin.getRow();

        // 본문 충돌 확인
        if (idempotencyService.isBodyConflict(slot, bodyHash)) { // 기존 키가 있는데 본문이 다르면 true -> 충돌남, 새로 만든 키거나 본문이 같으면 충돌 안남
            throw new CustomException(ErrorCode.IDEMPOTENCY_BODY_CONFLICT);
        }

        if (slot.getStatus() == IdemStatus.DONE) {
            // 스냅샷이 있으면 그대로, 없으면 리소스 재조회해서 응답 구성
            PrepaymentResponseDto replay;
            if (slot.getResponseJson() != null) { // DONE 인데, 응답 결과가 있다면 반환
                replay = parseSnapshot(slot.getResponseJson().toString());
            } else {
                throw new CustomException(ErrorCode.IDEMPOTENCY_REPLAY_UNAVAILABLE);
            }
            return IdempotentResult.okReplay(replay);
        }

        // 다른 처리에서 IN_PROGRESS로 선점
        if (!begin.isCreated() && slot.getStatus() == IdemStatus.IN_PROGRESS) { // IN_PROGRESS인데, 만들어지지 않았다면 (DONE 상태가 아니라면)
            return IdempotentResult.acceptedWithRetryAfterSeconds(2); // 2초뒤에 응답을 만들어줘
        }

        // 1. 사용자 정보 조회 및 검증
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.CUSTOMER_NOT_FOUND));

        String userKey = customer.getUserKey();

        if (userKey == null || userKey.trim().isEmpty()) {
            throw new CustomException(ErrorCode.USER_KEY_NOT_FOUND);
        }

        // 2. 가게 정보 조회 및 검증
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new CustomException(ErrorCode.STORE_NOT_FOUND));

        String merchantId = String.valueOf(store.getMerchantId());

        // 3. 사용자의 개인 지갑 조회 또는 생성
        Wallet wallet = findOrCreateIndividualWallet(customer);

        // 4. 외부 API 호출 (카드 결제) - CustomException이 자동으로 던져짐
        SsafyCardPaymentResponseDto apiResponse = ssafyFinanceApiService.requestCardPayment(
                userKey,
                requestDto.getCardNo(),
                requestDto.getCvc(),
                merchantId,
                requestDto.getPaymentBalance()
        );

        // 5. 보너스 포인트 계산
        long actualPaymentAmount = requestDto.getPaymentBalance();
        ChargeBonus chargeBonus = chargeBonusService.findChargeBonusByAmount(storeId, actualPaymentAmount).orElse(null);

        long totalPoints = actualPaymentAmount;
        int bonusPercentage = 0;
        long bonusAmount = 0;

        if (chargeBonus != null) {
            bonusPercentage = chargeBonus.getBonusPercentage();
            bonusAmount = actualPaymentAmount * bonusPercentage / 100;
            totalPoints = actualPaymentAmount + bonusAmount;
            log.info("보너스 적용 - 실제결제: {}원, 보너스: {}% ({}원), 총지급: {}포인트",
                    actualPaymentAmount, bonusPercentage, bonusAmount, totalPoints);
        }

        // 6. DB 업데이트 (트랜잭션 처리)
        PrepaymentResponseDto response = updateDatabaseAfterPayment(wallet, store, actualPaymentAmount, totalPoints, bonusPercentage, bonusAmount, apiResponse);

        // 멱등 완료 기록(DONE + 응답 스냅샷)
        idempotencyService.completeCharge(slot, HttpStatus.CREATED.value(), response);

        return IdempotentResult.created(response);
    }

    /**
     * 개인 지갑 조회 또는 생성
     */
    private Wallet findOrCreateIndividualWallet(Customer customer) {
        return walletRepository.findByCustomerAndWalletType(customer, WalletType.INDIVIDUAL)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .customer(customer)
                            .walletType(WalletType.INDIVIDUAL)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    /**
     * 결제 성공 후 DB 업데이트
     */
    private PrepaymentResponseDto updateDatabaseAfterPayment(
            Wallet wallet,
            Store store,
            long actualPaymentAmount,
            long totalPoints,
            int bonusPercentage,
            long bonusAmount,
            SsafyCardPaymentResponseDto apiResponse) {

        // 1. Transaction 생성 (총 지급 포인트로 기록)
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .customer(wallet.getCustomer())
                .store(store)
                .transactionType(TransactionType.CHARGE)
                .amount(totalPoints)
                .transactionUniqueNo(apiResponse.getRec().getTransactionUniqueNo())
                .build();
        transaction = transactionRepository.save(transaction);

        // 2. WalletStoreLot 생성 (총 지급 포인트로 생성, 만료일: 1년 후)
        LocalDateTime expiredAt = LocalDateTime.now().plusYears(1);
        WalletStoreLot lot = WalletStoreLot.builder()
                .wallet(wallet)
                .store(store)
                .amountTotal(totalPoints)
                .amountRemaining(totalPoints)
                .acquiredAt(LocalDateTime.now())
                .expiredAt(expiredAt)
                .sourceType(LotSourceType.CHARGE)
                .lotStatus(LotStatus.ACTIVE)
                .originChargeTransaction(transaction)
                .build();
        walletStoreLotRepository.save(lot);

        // 3. WalletStoreBalance 업데이트 또는 생성
        WalletStoreBalance balance = walletStoreBalanceRepository
                .findByWalletAndStore(wallet, store)
                .orElseGet(() -> WalletStoreBalance.builder()
                        .wallet(wallet)
                        .store(store)
                        .balance(0L)
                        .build());

        balance.addBalance(totalPoints);
        walletStoreBalanceRepository.save(balance);

        // 4. SettlementTask 생성 (실제 결제 금액으로 정산 예정)
        SettlementTask settlementTask = SettlementTask.builder()
                .transaction(transaction)
                .actualPaymentAmount(actualPaymentAmount)
                .status(SettlementTask.Status.PENDING)
                .build();
        settlementTaskRepository.save(settlementTask);

        // 5. 카드 결제 완료 이벤트 발행
        try {
            PaymentEvent paymentEvent = PaymentEvent.builder()
                    .customerId(wallet.getCustomer().getCustomerId())
                    .customerName(wallet.getCustomer().getName())
                    .storeId(store.getStoreId())
                    .storeName(store.getStoreName())
                    .ownerId(store.getOwner().getOwnerId())
                    .transactionId(transaction.getTransactionId())
                    .transactionUniqueNo(transaction.getTransactionUniqueNo())
                    .paymentAmount(actualPaymentAmount)
                    .totalPoints(totalPoints)
                    .bonusPercentage(bonusPercentage)
                    .bonusAmount(bonusAmount)
                    .transactionTime(transaction.getCreatedAt())
                    .build();

            kafkaEventProducer.publishPaymentEvent(paymentEvent);

            log.info("카드 결제 이벤트 발행 완료 - 고객ID: {}, 결제금액: {}, 총포인트: {}",
                    wallet.getCustomer().getCustomerId(), actualPaymentAmount, totalPoints);
        } catch (Exception e) {
            log.warn("카드 결제 이벤트 발행 실패 - 고객ID: {}, 오류: {}",
                    wallet.getCustomer().getCustomerId(), e.getMessage());
            // 이벤트 발행 실패는 비즈니스 로직에 영향을 주지 않음
        }

        // 6. 응답 생성
        long updatedBalance = balance.getBalance();

        return PrepaymentResponseDto.builder()
                .transactionId(transaction.getTransactionId())
                .transactionUniqueNo(apiResponse.getRec().getTransactionUniqueNo())
                .storeId(store.getStoreId())
                .storeName(store.getStoreName())
                .paymentAmount(actualPaymentAmount)
                .bonusPercentage(bonusPercentage)
                .bonusAmount(bonusAmount)
                .totalPoints(totalPoints)
                .transactionTime(transaction.getCreatedAt())
                .remainingBalance(updatedBalance)
                .build();
    }

    /**
     * 요청 바디 정규화 (키 정렬 + 공백 제거 등: ObjectMapper 설정에 따름)
     */
    private String canonicalizeRequestBody(PrepaymentRequestDto requestDto) {
        CanonicalPrepayment canonical = CanonicalPrepayment.builder()
                .cardNo(requestDto.getCardNo())
                .cvc(requestDto.getCvc())
                .paymentBalance(requestDto.getPaymentBalance())
                .build();

        try {
            return canonicalObjectMapper.writeValueAsString(canonical);
            // JSON 문자열로 변환
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.REQUEST_CANONICALIZE_FAILED);
        }
    }

    /**
     * 스냅샷 JSON → DTO
     */
    private PrepaymentResponseDto parseSnapshot(String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            return objectMapper.readValue(bytes, PrepaymentResponseDto.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RESPONSE_SNAPSHOT_PARSE_FAILED);
        }
    }

    /**
     * transactionUniqueNo로 리소스를 재조회하여 응답 재구성 (스냅샷 없을 때 폴백)
     */
    private PrepaymentResponseDto rebuildFromResource(UUID transactionUniqueNo) {
        Transaction transaction = transactionRepository.findByTransactionUniqueNo(transactionUniqueNo.toString())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSACTION_NOT_FOUND));

        WalletStoreBalance balance = walletStoreBalanceRepository
                .findByWalletAndStore(transaction.getWallet(), transaction.getStore())
                .orElseThrow(() -> new CustomException(ErrorCode.WALLET_BALANCE_NOT_FOUND));

        SettlementTask settlementTask = settlementTaskRepository.findByTransaction(transaction)
                .orElseThrow(() -> new CustomException(ErrorCode.SETTLEMENT_TASK_NOT_FOUND));

        long actualPaymentAmount = settlementTask.getActualPaymentAmount();
        long totalPoints = transaction.getAmount();
        long bonusAmount = totalPoints - actualPaymentAmount;
        int bonusPercentage = actualPaymentAmount > 0 ? (int) (bonusAmount * 100 / actualPaymentAmount) : 0;

        return PrepaymentResponseDto.builder()
                .transactionId(transaction.getTransactionId())
                .transactionUniqueNo(transaction.getTransactionUniqueNo())
                .storeId(transaction.getStore().getStoreId())
                .storeName(transaction.getStore().getStoreName())
                .paymentAmount(actualPaymentAmount)
                .bonusPercentage(bonusPercentage)
                .bonusAmount(bonusAmount)
                .totalPoints(totalPoints)
                .transactionTime(transaction.getCreatedAt())
                .remainingBalance(balance.getBalance())
                .build();
    }
}