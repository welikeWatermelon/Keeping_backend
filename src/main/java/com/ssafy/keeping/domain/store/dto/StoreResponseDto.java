package com.ssafy.keeping.domain.store.dto;

import com.ssafy.keeping.domain.store.constant.StoreStatus;
import com.ssafy.keeping.domain.store.model.Store;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class StoreResponseDto {
    // TODO: 추후 가게 주인으로서 조회하는 용으로 쓸때 사용 and bankAccount, taxId 등은 마스킹 필요
    private Long storeId;
    private String storeName;
    private String address;
    private String phoneNumber;
    private Long merchantId;
    private String category;
    private StoreStatus storeStatus;
    private String description;
    private LocalDateTime createdAt;
    private String imgUrl;

    public static StoreResponseDto fromEntity(Store store) {
        return StoreResponseDto.builder()
                .storeId(store.getStoreId())
                .storeName(store.getStoreName())
                .address(store.getAddress())
                .phoneNumber(store.getPhoneNumber())
                .merchantId(store.getMerchantId())
                .category(store.getCategory())
                .createdAt(store.getCreatedAt())
                .storeStatus(store.getStoreStatus())
                .description(store.getDescription())
                .imgUrl(store.getImgUrl())
                .build();
    }
}
