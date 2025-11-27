package com.ssafy.keeping.domain.payment.qr.service;

import com.ssafy.keeping.domain.payment.common.IdUtil;
import com.ssafy.keeping.domain.payment.qr.dto.QrCreateRequest;
import com.ssafy.keeping.domain.payment.qr.dto.QrCreateResponse;
import com.ssafy.keeping.domain.payment.qr.model.QrToken;
import com.ssafy.keeping.domain.payment.qr.repository.QrTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrTokenService {

    private final QrTokenRepository qrTokenRepository;
    private final Clock clock;

    @Transactional
    public QrCreateResponse create(Long customerId, QrCreateRequest req) {

        UUID id = IdUtil.newUuidV7();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expires = now.plusSeconds(req.getTtlSeconds()); // 토큰 만료 시각

        QrToken entity = QrToken.builder()
                .qrTokenId(id)
                .customerId(customerId)
                .walletId(req.getWalletId())
                .mode(req.getMode())
                .bindStoreId(req.getBindStoreId())
                .expiresAt(expires)
                .build();

        QrToken saved = qrTokenRepository.save(entity);
        return QrCreateResponse.from(saved);
    }
}
