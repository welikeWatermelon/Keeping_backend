package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardInquiryRecDto {

    private String cardNo;                    // 카드번호
    private String cvc;                       // CVC 번호
    private String cardUniqueNo;              // 카드고유번호
    private String cardIssuerCode;            // 카드발급기관코드
    private String cardIssuerName;            // 카드발급기관명
    private String cardName;                  // 카드명
    private String baselinePerformance;       // 기준실적
    private String maxBenefitLimit;           // 최대혜택한도
    private String cardDescription;           // 카드설명
    private String cardExpiryDate;            // 카드만료일자
    private String withdrawalAccountNo;       // 출금계좌번호
    private String withdrawalDate;            // 출금일
}