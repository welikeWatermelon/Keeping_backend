package com.ssafy.keeping.domain.payment.qr.constant;

public enum QrState {
    ISSUED, // 발급됨
    CONSUMED, // 소비됨 (사용 완료)
    EXPIRED, // 만료됨
    REVOKED // 사용자 취소
}