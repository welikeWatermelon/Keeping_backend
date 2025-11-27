package com.ssafy.keeping.domain.charge.service;

import com.ssafy.keeping.domain.charge.dto.request.CancelRequestDto;
import com.ssafy.keeping.domain.charge.dto.response.CancelListResponseDto;
import com.ssafy.keeping.domain.charge.dto.response.CancelResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardCancelResponseDto;
import com.ssafy.keeping.domain.charge.model.SettlementTask;
import com.ssafy.keeping.domain.charge.repository.SettlementTaskRepository;
import com.ssafy.keeping.domain.payment.transactions.constant.TransactionType;
import com.ssafy.keeping.domain.payment.transactions.model.Transaction;
import com.ssafy.keeping.domain.payment.transactions.repository.TransactionRepository;
import com.ssafy.keeping.domain.wallet.model.WalletStoreBalance;
import com.ssafy.keeping.domain.wallet.model.WalletStoreLot;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreBalanceRepository;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreLotRepository;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.event.service.KafkaEventProducer;
import com.ssafy.keeping.domain.event.dto.CancelEvent;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CancelService {

    private final SettlementTaskRepository settlementTaskRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final WalletStoreLotRepository walletStoreLotRepository;
    private final WalletStoreBalanceRepository walletStoreBalanceRepository;
    private final SsafyFinanceApiService ssafyFinanceApiService;
    private final KafkaEventProducer kafkaEventProducer;

    /**
     * 취소 가능한 거래 목록 조회 (페이지네이션)
     */
    public Page<CancelListResponseDto> getCancelableTransactions(Long customerId, Pageable pageable) {
        // 1. 고객 존재 여부 확인
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.CUSTOMER_NOT_FOUND));

        log.info("취소 가능한 거래 목록 조회 - 고객ID: {}, 페이지: {}, 크기: {}", 
                customerId, pageable.getPageNumber(), pageable.getPageSize());

        // 2. 취소 가능한 거래 조회
        Page<SettlementTask> cancelableTasks = settlementTaskRepository
                .findCancelableTransactions(customerId, pageable);

        // 3. DTO 변환
        return cancelableTasks.map(this::convertToDto);
    }

    /**
     * 카드 결제 취소 처리
     */
    @Transactional
    public CancelResponseDto cancelPayment(Long customerId, CancelRequestDto requestDto) {
        log.info("카드 결제 취소 처리 시작 - 고객ID: {}, 거래번호: {}", customerId, requestDto.getTransactionUniqueNo());

        // 1. 취소 가능 검증
        Transaction originalTransaction = validateCancellation(requestDto.getTransactionUniqueNo());
        
        // 2. 권한 검증 (본인의 거래인지 확인)
        if (!originalTransaction.getCustomer().getCustomerId().equals(customerId)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        
        // 3. 고객 정보 조회 (userKey 필요)
        Customer customer = originalTransaction.getCustomer();
        String userKey = customer.getUserKey();
        
        if (userKey == null || userKey.trim().isEmpty()) {
            throw new CustomException(ErrorCode.USER_KEY_NOT_FOUND);
        }
        
        // 3. 외부 API 호출 (실제 취소 처리)
        log.info("외부 API 취소 호출 시작 - 거래번호: {}, userKey: {}", 
                requestDto.getTransactionUniqueNo(), userKey);
        
        SsafyCardCancelResponseDto apiResponse = ssafyFinanceApiService.requestCardCancel(
                userKey,
                requestDto.getCardNo(),
                requestDto.getCvc(),
                requestDto.getTransactionUniqueNo()
        );
        
        log.info("외부 API 취소 성공 - 취소 거래번호: {}", 
                apiResponse.getRec().getTransactionUniqueNo());
        
        // 4. DB 반영 (외부 API 성공 후)
        return updateDatabaseAfterCancel(originalTransaction, apiResponse);
    }

    /**
     * 취소 가능 검증
     */
    private Transaction validateCancellation(String transactionUniqueNo) {
        // 1. transactionUniqueNo로 원본 거래 조회
        Transaction originalTransaction = transactionRepository
                .findByTransactionUniqueNo(transactionUniqueNo)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSACTION_NOT_FOUND));

        // 2. settlement_task 상태 확인 (PENDING인지)
        SettlementTask settlementTask = settlementTaskRepository
                .findByTransaction(originalTransaction)
                .orElseThrow(() -> new CustomException(ErrorCode.SETTLEMENT_TASK_NOT_FOUND));

        if (!settlementTask.getStatus().equals(SettlementTask.Status.PENDING)) {
            throw new CustomException(ErrorCode.CANCEL_NOT_AVAILABLE);
        }

        // 3. wallet_store_lot에서 잔여 포인트 확인 (충전된 전체 포인트가 남아있는지)
        WalletStoreLot lot = walletStoreLotRepository
                .findByOriginChargeTransaction(originalTransaction)
                .orElseThrow(() -> new CustomException(ErrorCode.WALLET_NOT_FOUND));

        // 충전된 전체 금액(totalPoints)과 현재 잔여 포인트 비교
        if (!lot.getAmountRemaining().equals(originalTransaction.getAmount())) {
            log.warn("취소 불가 - 포인트가 이미 사용됨. 총 포인트: {}, 잔여 포인트: {}",
                    originalTransaction.getAmount(), lot.getAmountRemaining());
            throw new CustomException(ErrorCode.CANCEL_NOT_AVAILABLE);
        }

        log.info("취소 가능 검증 완료 - 거래ID: {}, 실제결제금액: {}, 총포인트: {}",
                originalTransaction.getTransactionId(), settlementTask.getActualPaymentAmount(), originalTransaction.getAmount());

        return originalTransaction;
    }

    /**
     * 취소 성공 후 DB 업데이트
     */
    private CancelResponseDto updateDatabaseAfterCancel(
            Transaction originalTransaction, 
            SsafyCardCancelResponseDto apiResponse) {
        
        log.info("DB 반영 로직 시작 - 원본 거래ID: {}", originalTransaction.getTransactionId());

        // 1. settlement_tasks 상태를 CANCELED로 변경
        SettlementTask settlementTask = settlementTaskRepository
                .findByTransaction(originalTransaction)
                .orElseThrow(() -> new CustomException(ErrorCode.SETTLEMENT_TASK_NOT_FOUND));
        
        settlementTask.markAsCanceled();
        log.info("SettlementTask 상태 CANCELED로 변경 완료");

        // 2. 새로운 취소 Transaction 레코드 생성 (CANCEL 타입)
        Transaction cancelTransaction = Transaction.builder()
                .wallet(originalTransaction.getWallet())
                .customer(originalTransaction.getCustomer())
                .store(originalTransaction.getStore())
                .transactionType(TransactionType.CANCEL_CHARGE)
                .amount(originalTransaction.getAmount()) // 양수로 저장
                .transactionUniqueNo(originalTransaction.getTransactionUniqueNo()) // 동일한 거래번호
                .refTransaction(originalTransaction)
                .build();

        cancelTransaction = transactionRepository.save(cancelTransaction);
        log.info("취소 Transaction 생성 완료 - ID: {}", cancelTransaction.getTransactionId());

        // 3. wallet_store_lot 상태를 CANCELED로 변경
        WalletStoreLot lot = walletStoreLotRepository
                .findByOriginChargeTransaction(originalTransaction)
                .orElseThrow(() -> new CustomException(ErrorCode.WALLET_NOT_FOUND));

        lot.setAmountRemaining(0L);
        lot.setCancelTransaction(cancelTransaction); // cancel_tx_id 설정
        lot.setCanceledAt(LocalDateTime.now()); // canceled_at 설정
        lot.markAsCanceled(); // lot_status를 CANCELED로 변경
        log.info("WalletStoreLot 상태 CANCELED로 변경 완료 - Lot ID: {}", lot.getLotId());

        // 4. wallet_store_balance 금액 차감
        WalletStoreBalance balance = walletStoreBalanceRepository
                .findByWalletAndStore(originalTransaction.getWallet(), originalTransaction.getStore())
                .orElseThrow(() -> new CustomException(ErrorCode.WALLET_NOT_FOUND));
        
        balance.subtractBalance(originalTransaction.getAmount());
        log.info("WalletStoreBalance 차감 완료 - 차감 금액: {}, 잔여 잔액: {}",
                originalTransaction.getAmount(), balance.getBalance());

        // 5. 카드 결제 취소 완료 이벤트 발행
        try {
            CancelEvent cancelEvent = CancelEvent.builder()
                    .customerId(originalTransaction.getCustomer().getCustomerId())
                    .customerName(originalTransaction.getCustomer().getName())
                    .storeId(originalTransaction.getStore().getStoreId())
                    .storeName(originalTransaction.getStore().getStoreName())
                    .ownerId(originalTransaction.getStore().getOwner().getOwnerId())
                    .cancelTransactionId(cancelTransaction.getTransactionId())
                    .transactionUniqueNo(originalTransaction.getTransactionUniqueNo())
                    .cancelAmount(settlementTask.getActualPaymentAmount())
                    .cancelTime(LocalDateTime.now())
                    .build();

            kafkaEventProducer.publishCancelEvent(cancelEvent);

            log.info("카드 취소 이벤트 발행 완료 - 고객ID: {}, 취소금액: {}",
                    originalTransaction.getCustomer().getCustomerId(), settlementTask.getActualPaymentAmount());
        } catch (Exception e) {
            log.warn("카드 취소 이벤트 발행 실패 - 고객ID: {}, 오류: {}",
                    originalTransaction.getCustomer().getCustomerId(), e.getMessage());
            // 이벤트 발행 실패는 비즈니스 로직에 영향을 주지 않음
        }

        // 6. 응답 생성 (실제 결제금액과 포인트 구분)
        return CancelResponseDto.builder()
                .cancelTransactionId(cancelTransaction.getTransactionId())
                .transactionUniqueNo(originalTransaction.getTransactionUniqueNo())
                .cancelAmount(settlementTask.getActualPaymentAmount()) // 이미 위에서 조회한 settlementTask 사용
                .cancelTime(LocalDateTime.now())
                .remainingBalance(balance.getBalance())
                .build();
    }

    /**
     * SettlementTask를 CancelListResponseDto로 변환
     */
    private CancelListResponseDto convertToDto(SettlementTask settlementTask) {
        // WalletStoreLot에서 잔여 포인트 조회
        WalletStoreLot lot = walletStoreLotRepository
                .findByOriginChargeTransaction(settlementTask.getTransaction())
                .orElseThrow(() -> new CustomException(ErrorCode.WALLET_NOT_FOUND));

        return CancelListResponseDto.builder()
                .transactionUniqueNo(settlementTask.getTransaction().getTransactionUniqueNo())
                .storeName(settlementTask.getTransaction().getStore().getStoreName())
                .paymentAmount(settlementTask.getActualPaymentAmount()) // 실제 결제금액 사용
                .transactionTime(settlementTask.getTransaction().getCreatedAt())
                .remainingBalance(lot.getAmountRemaining()) // 실제 잔여 포인트 사용
                .build();
    }
}