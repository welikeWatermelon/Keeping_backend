package com.ssafy.keeping.domain.payment.intent.repository;

import com.ssafy.keeping.domain.payment.intent.constant.PaymentStatus;
import com.ssafy.keeping.domain.payment.intent.model.PaymentIntent;
import com.ssafy.keeping.domain.payment.qr.model.QrToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {
    // 외부 공개용 ID로 조회
    Optional<PaymentIntent> findByPublicId(UUID publicId);

    // 멱등성 키로 조회
    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

    // QR Token으로 조회
    Optional<PaymentIntent> findByQrToken(QrToken qrToken);

    // QrToken으로 조회 (상태 조건까지 포함)
    Optional<PaymentIntent> findByQrTokenAndStatus(QrToken qrToken, PaymentStatus status);

    Optional<PaymentIntent> findByPublicIdAndCustomerId(UUID publicId, Long customerId);

    boolean existsByPublicId(UUID publicId);

    // 특정 QrToken에 대해 PaymentIntent가 존재하는지 여부만 체크
    boolean existsByQrToken(QrToken qrToken);

    // 만료 배치(예: PENDING→EXPIRED 전환 탐지용)
    // 지정한 상태 + 만료시각보다 이전인 Intent들을 모두 가져오기
    // 아직 승인 안 된 PENDING intent 중, 만료시각이 지난 애들 찾아서 상태를 EXPIRED로 바꿔야 함
    List<PaymentIntent> findAllByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime threshold);

    // 청소용
    long deleteByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime threshold);
}