package com.ssafy.keeping.domain.auth.dto;

import com.ssafy.keeping.domain.user.owner.model.Owner;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerResponse {
    private Long id;
    private String userType; // "owner"
    private String email;
    private String name;

    // Owner 엔티티에서 OwnerResponse로 변환하는 정적 메서드
    public static OwnerResponse from(Owner owner) {
        return OwnerResponse.builder()
                .id(owner.getOwnerId())
                .userType("owner")
                .email(owner.getEmail())
                .name(owner.getName())
                .build();
    }
}