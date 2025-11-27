package com.ssafy.keeping.domain.payment.intent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Schema(description = "점주 결제요청 생성 바디(금액은 서버가 계산)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateRequest {

    @Schema(description = "요청 매장 PK(QrToken 클래스의 bindStoreId와 일치해야함)", example = "12345", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    private Long storeId;

    @Schema(description = "주문 항목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty
    @Valid
    private List<PaymentInitiateItemDto> orderItems;

}