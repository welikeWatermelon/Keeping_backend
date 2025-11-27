package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardPaymentRecDto {

    private String transactionUniqueNo;
    private String categoryId;
    private String categoryName;
    private String merchantId;
    private String merchantName;
    private String transactionDate;
    private String transactionTime;
    private String paymentBalance;

    public BigDecimal getPaymentBalanceAsBigDecimal() {
        return new BigDecimal(paymentBalance);
    }
}