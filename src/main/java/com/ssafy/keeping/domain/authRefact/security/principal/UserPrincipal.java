package com.ssafy.keeping.domain.authRefact.security.principal;

import com.ssafy.keeping.domain.authRefact.enums.UserRole;

public record UserPrincipal(
        Long id,
        UserRole role
) {}
