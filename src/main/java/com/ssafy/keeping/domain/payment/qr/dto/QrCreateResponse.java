package com.ssafy.keeping.domain.payment.qr.dto;

import com.ssafy.keeping.domain.payment.qr.constant.QrMode;
import com.ssafy.keeping.domain.payment.qr.model.QrToken;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QrCreateResponse {
    private String qrToken;
    private String expiresAt;
    private String qrUri;
    private QrMode mode;

    public static QrCreateResponse from(QrToken t) {
        String expiresIsoKst = t.getExpiresAt()
                .atOffset(java.time.ZoneOffset.ofHours(9))
                .toString();
        String qrUri = String.format("https://j13a509.p.ssafy.io/owners/qr/intent?v=%d&t=%s&m=%s",
                1, t.getQrTokenId(), t.getMode().name());

        return QrCreateResponse.builder()
                .qrToken(t.getQrTokenId().toString())
                .expiresAt(expiresIsoKst)
                .qrUri(qrUri)
                .mode(t.getMode())
                .build();
    }
}