package com.ssafy.keeping.domain.user.customer.dto;

import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardInquiryRecDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerCardResponse {

    private String cardNo;      // 카드번호
    private String cvc;         // CVC 번호
    private String cardName;    // 카드명

    public static CustomerCardResponse from(SsafyCardInquiryRecDto rec) {
        return CustomerCardResponse.builder()
                .cardNo(rec.getCardNo())
                .cvc(rec.getCvc())
                .cardName(rec.getCardName())
                .build();
    }
}