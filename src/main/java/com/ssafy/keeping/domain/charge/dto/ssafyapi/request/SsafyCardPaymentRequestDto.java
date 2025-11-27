package com.ssafy.keeping.domain.charge.dto.ssafyapi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardPaymentRequestDto {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    private String cardNo;
    private String cvc;
    private String merchantId;
    private String paymentBalance;

    public static SsafyCardPaymentRequestDto create(
            SsafyApiHeaderDto header,
            String cardNo,
            String cvc,
            String merchantId,
            long paymentBalance) {
        
        return SsafyCardPaymentRequestDto.builder()
                .header(header)
                .cardNo(cardNo)
                .cvc(cvc)
                .merchantId(merchantId)
                .paymentBalance(paymentBalance + "")
                .build();
    }
}