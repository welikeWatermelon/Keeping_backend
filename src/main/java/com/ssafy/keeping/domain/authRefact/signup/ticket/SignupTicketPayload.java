package com.ssafy.keeping.domain.authRefact.signup.ticket;

import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.authRefact.enums.UserRole;

public record SignupTicketPayload(
        AuthProvider providerType,
        String providerId,
        UserRole role,
        String nickname,
        String profileUrl,
        long issuedAtEpochSec // 티켓이 발급된 시각
) {
}
