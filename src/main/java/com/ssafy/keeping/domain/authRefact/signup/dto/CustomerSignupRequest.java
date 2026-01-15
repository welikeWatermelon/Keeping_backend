package com.ssafy.keeping.domain.authRefact.signup.dto;

import com.ssafy.keeping.domain.authRefact.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CustomerSignupRequest(
        @NotBlank
        String ticket,

        @NotBlank
        String name,

        @NotNull
        LocalDate birth,

        @NotNull
        Gender gender,

        @NotBlank
        String email,

        @NotBlank
        String phoneNumber,

        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "PIN은 숫자 6자리여야 합니다")
        String pin
) {}
