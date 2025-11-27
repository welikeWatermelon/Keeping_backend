package com.ssafy.keeping.domain.payment.intent.dto;

import com.ssafy.keeping.domain.payment.intent.constant.PaymentStatus;
import com.ssafy.keeping.domain.payment.intent.model.PaymentIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "결제 의도 상세 응답(생성/조회 공용)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentDetailResponse {

    @Schema(description = "외부노출 Intent ID(UUID 문자열)", example = "6f3f71c0-9b0c-4c33-a7c8-7d3e9745f2da")
    private String intentId;

    @Schema(description = "매장 PK", example = "12345")
    private Long storeId;

    @Schema(description = "손님 PK", example = "12")
    private Long customerId;

    @Schema(description = "총 결제 요청 금액(원)", example = "12000")
    private Long amount;

    @Schema(description = "상태", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "생성시각(ISO+09:00)")
    private String createdAt;

    @Schema(description = "만료시각(ISO+09:00)")
    private String expiresAt;

    @Schema(description = "승인시각(ISO+09:00)")
    private String approvedAt;

    @Schema(description = "거절시각(ISO+09:00)")
    private String declinedAt;

    @Schema(description = "취소시각(ISO+09:00)")
    private String canceledAt;

    @Schema(description = "결제확정 완료시각(ISO+09:00)")
    private String completedAt;

    @Schema(description = "주문 항목 스냅샷 목록")
    private List<PaymentIntentItemView> items;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static String toIsoKst(LocalDateTime t) {
        return (t == null) ? null : t.atZone(KST).toOffsetDateTime().format(ISO_OFFSET);
    }

    /** 엔티티 + 아이템뷰 목록 → 상세 응답으로 변환 (KST ISO) */
    public static PaymentIntentDetailResponse from(PaymentIntent e, List<PaymentIntentItemView> items) {
        return PaymentIntentDetailResponse.builder()
                .intentId(e.getPublicId().toString())
                .storeId(e.getStoreId())
                .customerId(e.getCustomerId())
                .amount(e.getAmount())
                .status(e.getStatus())
                .createdAt(toIsoKst(e.getCreatedAt()))
                .expiresAt(toIsoKst(e.getExpiresAt()))
                .approvedAt(toIsoKst(e.getApprovedAt()))
                .declinedAt(toIsoKst(e.getDeclinedAt()))
                .canceledAt(toIsoKst(e.getCanceledAt()))
                .completedAt(toIsoKst(e.getCompletedAt()))
                .items(items)
                .build();
    }
}