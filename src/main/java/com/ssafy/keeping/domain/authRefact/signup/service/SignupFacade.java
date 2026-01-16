package com.ssafy.keeping.domain.authRefact.signup.service;

import com.ssafy.keeping.domain.authRefact.enums.UserRole;
import com.ssafy.keeping.domain.authRefact.signup.dto.*;
import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
import com.ssafy.keeping.domain.authRefact.token.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignupFacade {

    private final SignupTxService signupTxService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public IssuedAuthTokens<CustomerRegisterResponse> signupCustomer(CustomerSignupRequest req) {

        SignupResult<CustomerRegisterResponse> result = signupTxService.signupCustomerTx(req);

        return issueSuccessToken(result.userId(), result.role(), result.userDto()); // 커밋 이후에 토큰 발급/저장 수행
    }

    /**
     * 토큰 발급 + 응답 DTO 생성
     * access + refresh 모두 발급
     */
    private <T> IssuedAuthTokens<T> issueSuccessToken(long userId, UserRole role, T userDto) {
        String accessToken = accessTokenService.issueAccessToken(String.valueOf(userId), role);
        var issuedRefresh = refreshTokenService.issueSingleSession(userId, role);

        SignupResponse<T> body = new SignupResponse<>(
                userDto,
                AuthResponse.bearer(accessToken, role)
        );

        return new IssuedAuthTokens<>(body, issuedRefresh.token(), issuedRefresh.ttlSeconds());
    }
}
