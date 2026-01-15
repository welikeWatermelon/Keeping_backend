package com.ssafy.keeping.domain.authRefact.signup.controller;

import com.ssafy.keeping.domain.authRefact.signup.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    @PostMapping("/customer")
    public ResponseEntity<AuthTokenResponse> signupCustomer(@Valid @RequestBody CustomerSignupRequest req) {
        var issued = signupService.signupCustomer(req);

        ResponseCookie cookie = buildRefreshCookie(issued.refreshToken(), issued.refreshTtlSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(issued.body());
    }

    @PostMapping("/owner")
    public ResponseEntity<AuthTokenResponse> signupOwner(@Valid @RequestBody OwnerSignupRequest req) {
        var issued = signupService.signupOwner(req);

        ResponseCookie cookie = buildRefreshCookie(issued.refreshToken(), issued.refreshTtlSeconds());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(issued.body());
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, long ttlSeconds) {
        return ResponseCookie.from("REFRESH_TOKEN", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(ttlSeconds)
                .build();
    }
}
