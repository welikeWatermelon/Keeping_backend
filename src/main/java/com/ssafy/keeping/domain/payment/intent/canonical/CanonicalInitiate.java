package com.ssafy.keeping.domain.payment.intent.canonical;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonPropertyOrder({ "storeId", "items" }) // 필드 출력 순서 고정
public class CanonicalInitiate {

    private Long storeId;

    @JsonPropertyOrder({ "menuId", "quantity" }) // 필드 출력 순서 고정
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Item {
        private Long menuId;
        private int quantity;
    }

    private List<Item> items; // 정렬된 리스트로 세팅
}