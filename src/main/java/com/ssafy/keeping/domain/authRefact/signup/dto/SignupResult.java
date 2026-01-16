package com.ssafy.keeping.domain.authRefact.signup.dto;

import com.ssafy.keeping.domain.authRefact.enums.UserRole;

public record SignupResult<T>(
        long userId,
        UserRole role,
        T userDto
) {}