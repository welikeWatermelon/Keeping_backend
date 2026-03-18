package com.ssafy.keeping.qr.domain.intent.service;

import com.ssafy.keeping.qr.acl.WalletClient;
import com.ssafy.keeping.qr.acl.dto.PaymentCheckResponse;
import com.ssafy.keeping.qr.acl.dto.RefundRequest;
import com.ssafy.keeping.qr.acl.dto.RefundResponse;
import com.ssafy.keeping.qr.domain.intent.constant.PaymentStatus;
import com.ssafy.keeping.qr.domain.intent.model.PaymentIntent;
import com.ssafy.keeping.qr.domain.intent.repository.PaymentIntentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 결제 에러 복구 서비스
 * - UNCERTAIN 상태의 결제에 대해 실제 결제 여부 확인 후 보상 트랜잭션 수행
 * - @Scheduled + DB 플래그 방식
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final WalletClient walletClient;

    /** 복구 대상 존재 플래그 */
    private final AtomicBoolean hasRecoveryTarget = new AtomicBoolean(false);

    /** 중복 실행 방지 락 */
    private final AtomicBoolean isRecovering = new AtomicBoolean(false);

    /** 복구 대상 조회 범위 (최근 7일) */
    private static final int RECOVERY_DAYS = 7;

    /**
     * 서버 시작 시 복구 수행
     */
    @PostConstruct
    public void recoverOnStartup() {
        log.info("서버 시작 - 결제 복구 확인 시작");
        hasRecoveryTarget.set(true);
        recoverPeriodically();
    }

    /**
     * 복구 필요 플래그 설정 - FundsService에서 호출
     */
    public void markRecoveryNeeded() {
        hasRecoveryTarget.set(true);
        log.debug("복구 대상 플래그 설정됨");
    }

    /**
     * 주기적 복구 (10초 간격)
     * hasRecoveryTarget 플래그가 true일 때만 실제 복구 수행
     */
    @Scheduled(fixedRate = 10000)
    public void recoverPeriodically() {
        if (!hasRecoveryTarget.get()) {
            return;
        }

        if (!isRecovering.compareAndSet(false, true)) {
            log.debug("이미 복구 작업 진행 중 - 스킵");
            return;
        }

        try {
            doRecover();
        } finally {
            isRecovering.set(false);
        }
    }

    /**
     * 복구 대상 조회 및 개별 복구 수행
     */
    private void doRecover() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(RECOVERY_DAYS);

        List<PaymentIntent> targets = paymentIntentRepository.findRecoveryTargets(
                PaymentStatus.UNCERTAIN,
                PaymentStatus.PENDING,
                now,
                since
        );

        if (targets.isEmpty()) {
            hasRecoveryTarget.set(false);
            log.debug("복구 대상 없음 - 플래그 해제");
            return;
        }

        log.info("복구 대상 {}건 발견", targets.size());

        for (PaymentIntent intent : targets) {
            try {
                recoverSinglePayment(intent);
            } catch (Exception e) {
                log.error("결제 복구 실패: intentId={}, error={}", intent.getIntentId(), e.getMessage());
            }
        }
    }

    /**
     * 개별 결제 복구 로직
     * 1. 멱등성 키로 실제 결제 여부 확인
     * 2. 결제가 존재하면 환불 처리
     * 3. 결제가 없으면 ROLLED_BACK으로 마킹 (추가 작업 불필요)
     */
    @Transactional
    public void recoverSinglePayment(PaymentIntent intent) {
        String idempotencyKey = generateIdempotencyKey(intent);
        LocalDateTime now = LocalDateTime.now();

        log.info("결제 복구 시작: intentId={}, idempotencyKey={}", intent.getIntentId(), idempotencyKey);

        // 1. 실제 결제 여부 확인
        PaymentCheckResponse checkResult = walletClient.checkPayment(idempotencyKey);

        if (checkResult == null || !checkResult.isExists()) {
            // 결제가 실제로 일어나지 않음 - 별도 환불 불필요
            intent.markRolledBack(now, "결제 미발생 확인 - 복구 완료");
            paymentIntentRepository.save(intent);
            log.info("결제 미발생 확인: intentId={}", intent.getIntentId());
            return;
        }

        // 2. 결제가 존재하면 환불 처리
        String refundIdempotencyKey = generateRefundIdempotencyKey(intent);

        RefundRequest refundRequest = RefundRequest.builder()
                .walletId(intent.getWalletId())
                .storeId(intent.getStoreId())
                .amount(intent.getAmount())
                .originalTransactionId(checkResult.getTransactionId())
                .reason("UNCERTAIN 상태 자동 복구")
                .build();

        RefundResponse refundResult = walletClient.refund(refundRequest, refundIdempotencyKey);

        if (refundResult != null && refundResult.isSuccess()) {
            intent.markRolledBack(now, "환불 완료 - txId: " + refundResult.getRefundTransactionId());
            paymentIntentRepository.save(intent);
            log.info("결제 환불 완료: intentId={}, refundTxId={}",
                    intent.getIntentId(), refundResult.getRefundTransactionId());
        } else {
            log.error("환불 실패: intentId={}, response={}", intent.getIntentId(), refundResult);
            throw new RuntimeException("환불 처리 실패");
        }
    }

    /**
     * PaymentIntent 기반 결정적 멱등성 키 생성
     */
    private String generateIdempotencyKey(PaymentIntent intent) {
        return UUID.nameUUIDFromBytes(
                ("capture:" + intent.getPublicId().toString()).getBytes()
        ).toString();
    }

    /**
     * 환불용 멱등성 키 생성
     */
    private String generateRefundIdempotencyKey(PaymentIntent intent) {
        return UUID.nameUUIDFromBytes(
                ("refund:" + intent.getPublicId().toString()).getBytes()
        ).toString();
    }
}
