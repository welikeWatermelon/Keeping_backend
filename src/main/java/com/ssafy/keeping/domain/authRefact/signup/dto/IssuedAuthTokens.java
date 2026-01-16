package com.ssafy.keeping.domain.authRefact.signup.dto;

public record IssuedAuthTokens<T>(
        SignupResponse<T> body,
        String refreshToken,
        long refreshTtlSeconds
) {
}
