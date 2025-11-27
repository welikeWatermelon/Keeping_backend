package com.ssafy.keeping.domain.payment.qr.repository;

import com.ssafy.keeping.domain.payment.qr.constant.QrState;
import com.ssafy.keeping.domain.payment.qr.model.QrToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface QrTokenRepository extends JpaRepository<QrToken, UUID> {

    Optional<QrToken> findByQrTokenIdAndState(UUID qrTokenId, QrState state);
    long deleteByStateAndExpiresAtBefore(QrState state, LocalDateTime threshold); // 청소용

}
