package com.ssafy.keeping.domain.authRefact.signup.dto;

public record SignupResponse<T>(
        T user,
        AuthResponse auth
) {
    public static <T> SignupResponse<T> of(T user, AuthResponse auth) {
        return new SignupResponse<>(user, auth);
    }
}
