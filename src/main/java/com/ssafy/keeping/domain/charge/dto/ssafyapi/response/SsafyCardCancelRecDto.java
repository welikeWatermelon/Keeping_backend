package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardCancelRecDto {
    
    private String transactionUniqueNo;
    private String categoryId;
    private String categoryName;
    private String merchantId;
    private String merchantName;
    private String transactionDate;
    private String transactionTime;
    private String transactionBalance;
    private String status;
}