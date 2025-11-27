package com.ssafy.keeping.domain.payment.intent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Schema(description = "의도 상세조회용(스냅샷)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentItemView {

    @Schema(description = "당시 메뉴 PK", example = "10001")
    private Long menuId;

    @Schema(description = "스냅샷된 메뉴명", example = "등심 샤브샤브")
    private String name;

    @Schema(description = "스냅샷 단가(원)", example = "6000")
    private Long unitPrice;

    @Schema(description = "수량", example = "2")
    private int quantity;

    @Schema(description = "합계(원)", example = "12000")
    private Long lineTotal;

}