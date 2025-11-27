package com.ssafy.keeping.domain.charge.dto.ssafyapi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyAccountDepositRequestDto {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    private String accountNo;
    private String transactionBalance;
    private String transactionSummary;

    public static SsafyAccountDepositRequestDto create(
            SsafyApiHeaderDto header,
            String accountNo,
            Long transactionBalance,
            String transactionSummary) {
        
        return SsafyAccountDepositRequestDto.builder()
                .header(header)
                .accountNo(accountNo)
                .transactionBalance(transactionBalance.toString())
                .transactionSummary(transactionSummary)
                .build();
    }
}