package com.ssafy.keeping.domain.payment.qr.model;

import com.ssafy.keeping.domain.payment.qr.constant.QrMode;
import com.ssafy.keeping.domain.payment.qr.constant.QrState;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "qr_token", indexes = {
        @Index(name="idx_user_created", columnList = "customer_id, created_at"),
        @Index(name="idx_store_expires", columnList = "bind_store_id, expires_at"),
        @Index(name="idx_state_expires", columnList = "state, expires_at"),
        @Index(name = "idx_wallet_state",   columnList = "wallet_id, state")
})
public class QrToken {

    @Id
    @Column(name = "qr_token_id", columnDefinition = "BINARY(16)")
    private UUID qrTokenId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private QrMode mode;

    @Column(name = "bind_store_id", nullable = false)
    private Long bindStoreId;

    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime expiresAt; // 토큰 만료 시각

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private QrState state = QrState.ISSUED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 토큰 발급 시각

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt; // 토큰 처리 시각

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
        if (this.state == null) this.state = QrState.ISSUED;
    }
}