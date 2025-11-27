package com.ssafy.keeping.domain.user.owner.dto;

import com.ssafy.keeping.domain.auth.service.TokenResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupOwnerResponse {
    private OwnerRegisterResponse user;
    private TokenResponse token;
}

