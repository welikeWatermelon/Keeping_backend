package com.ssafy.keeping.domain.user.finopenapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCardRecResponse {

    private String cardNo;
    private String cvc;
    private String cardUniqueNo;
    private String cardIssuerCode;
    private String cardIssuerName;
    private String cardName;
    private Long baseLinePerformance;
    private Long maxBenefitLimit;
    private String cardDescription;
    private String cardExpiryDate;
    private String withdrawlAccountNo;
    private String withdrawlDate;

}
