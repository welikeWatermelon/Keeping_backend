package com.ssafy.keeping.domain.payment.intent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Schema(description = "결제요청 항목(메뉴 식별자 + 수량)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateItemDto {

    @Schema(description = "메뉴 PK", example = "10001")
    @NotNull
    private Long menuId;

    @Schema(description = "수량", example = "2", minimum = "1")
    @Positive
    private int quantity;

}