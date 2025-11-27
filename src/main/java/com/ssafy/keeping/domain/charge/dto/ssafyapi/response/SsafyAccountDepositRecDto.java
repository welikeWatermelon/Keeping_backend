package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyAccountDepositRecDto {

    private String transactionUniqueNo;
    private String transactionDate;
}