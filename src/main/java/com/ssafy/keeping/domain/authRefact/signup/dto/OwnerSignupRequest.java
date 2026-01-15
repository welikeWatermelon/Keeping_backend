package com.ssafy.keeping.domain.authRefact.signup.dto;

import com.ssafy.keeping.domain.authRefact.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record OwnerSignupRequest(
        @NotBlank String ticket,
        @NotBlank String name,
        @NotNull LocalDate birth,
        @NotNull Gender gender,
        @NotBlank String phoneNumber
) {}
