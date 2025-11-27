package com.ssafy.keeping.domain.payment.qr.dto;

import com.ssafy.keeping.domain.payment.qr.constant.QrMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QrCreateRequest {

    @NotNull
    private Long walletId;

    @NotNull
    private QrMode mode;

    @NotNull
    private Long bindStoreId;

    @Min(10)
    @Max(300)
    private int ttlSeconds;

}