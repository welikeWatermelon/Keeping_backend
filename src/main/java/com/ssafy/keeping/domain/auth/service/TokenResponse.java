package com.ssafy.keeping.domain.auth.service;

import com.ssafy.keeping.domain.auth.enums.UserRole;
import lombok.*;

@Getter
@Builder
public class TokenResponse {
    private Long userId;
    private UserRole role;
    private String accessToken;
    private String refreshToken;

    public TokenResponse withoutRefreshToken() {
        return TokenResponse.builder()
                .userId(userId)
                .role(role)
                .accessToken(accessToken)
                .build();
    }
}
