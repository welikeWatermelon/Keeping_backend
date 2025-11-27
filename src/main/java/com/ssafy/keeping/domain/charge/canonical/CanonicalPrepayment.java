package com.ssafy.keeping.domain.charge.canonical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanonicalPrepayment {

    private String cardNo;
    private String cvc;
    private Long paymentBalance;
}
