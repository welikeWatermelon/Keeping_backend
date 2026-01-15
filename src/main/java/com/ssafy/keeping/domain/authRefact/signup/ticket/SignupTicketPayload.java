package com.ssafy.keeping.domain.authRefact.signup.ticket;

import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.authRefact.enums.UserRole;

public record SignupTicketPayload(
        AuthProvider providerType,
        String providerId,
        UserRole role,
        long issuedAtEpochSec // String nonce 이게 없어지고 들어옴...??
) {
}
