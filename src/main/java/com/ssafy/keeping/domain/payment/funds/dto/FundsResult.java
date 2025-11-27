package com.ssafy.keeping.domain.payment.funds.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FundsResult {
    private final boolean sufficient;   // 잔액 충분 여부
    private final boolean policyOk;     // 정책 위반 여부
    private final Long transactionId;   // 생성된 거래 PK (성공 시)

    public static FundsResult insufficient() { return new FundsResult(false, true, null); }
    public static FundsResult policyViolation() { return new FundsResult(true, false, null); }
    public static FundsResult ok(Long txId) { return new FundsResult(true, true, txId); }

    public boolean isSufficient() { return sufficient; }
    public boolean isPolicyOk() { return policyOk; }
}
