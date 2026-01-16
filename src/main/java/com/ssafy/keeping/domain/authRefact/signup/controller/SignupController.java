package com.ssafy.keeping.domain.authRefact.signup.controller;

import com.ssafy.keeping.domain.authRefact.signup.dto.CustomerRegisterResponse;
import com.ssafy.keeping.domain.authRefact.signup.dto.CustomerSignupRequest;
import com.ssafy.keeping.domain.authRefact.signup.dto.OwnerSignupRequest;
import com.ssafy.keeping.domain.authRefact.signup.dto.SignupResponse;
import com.ssafy.keeping.domain.authRefact.signup.service.SignupFacade;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupFacade signupFacade;

    @PostMapping("/customer")
    public ResponseEntity<ApiResponse<SignupResponse<CustomerRegisterResponse>>> signupCustomer(@Valid @RequestBody CustomerSignupRequest req) {
        var issued = signupFacade.signupCustomer(req);

        ResponseCookie cookie = buildRefreshCookie(issued.refreshToken(), issued.refreshTtlSeconds());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("고객 회원가입 완료", HttpStatus.CREATED.value(), issued.body()));
    }

    @PostMapping("/owner")
    public ResponseEntity<ApiResponse<SignupResponse>> signupOwner(@Valid @RequestBody OwnerSignupRequest req) {
        var issued = signupService.signupOwner(req);

        ResponseCookie cookie = buildRefreshCookie(issued.refreshToken(), issued.refreshTtlSeconds());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("점주 회원가입 완료", HttpStatus.CREATED.value(), issued.body()));
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, long ttlSeconds) {
        return ResponseCookie.from("REFRESH_TOKEN", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(ttlSeconds)
                .build();
    }
}
