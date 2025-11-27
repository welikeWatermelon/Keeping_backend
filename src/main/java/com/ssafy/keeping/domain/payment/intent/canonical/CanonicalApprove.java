package com.ssafy.keeping.domain.payment.intent.canonical;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 승인 요청 바디의 캔노니컬 DTO
 * - 필드 순서 고정
 * - NULL 필드 제외
 * - pin 외 필드가 추가되더라도 직렬화 순서 안정성 유지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "pin" })
public class CanonicalApprove {
    private String pin;
}