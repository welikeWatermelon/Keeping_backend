package com.ssafy.keeping.domain.authRefact.signup.ticket;

import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.authRefact.enums.UserRole;

import java.time.Instant;

public class SignupTicketPayloadFactory {

    public static SignupTicketPayload payload(
            UserRole role,
            AuthProvider providerType,
            String providerId
    ) {
        return new SignupTicketPayload(
                providerType,
                providerId,
                role,
                Instant.now().getEpochSecond()
        );
    }
}
