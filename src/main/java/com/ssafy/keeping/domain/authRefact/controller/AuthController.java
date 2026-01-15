package com.ssafy.keeping.domain.authRefact.controller;

import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
import com.ssafy.keeping.domain.authRefact.token.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "REFRESH_TOKEN";
    private static final String REFRESH_COOKIE_PATH = "/auth"; // 발급할 때 path와 반드시 동일해야 삭제됨

    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);

        // 쿠키가 없으면 재발급 불가
        if (refreshToken == null || refreshToken.isBlank()) {
            // 지금은 간단히 401로 응답(나중에 CustomException(ErrorCode.REFRESH_TOKEN_MISSING)로 바꿔도 됨)
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "리프레시 토큰이 없습니다."
            ));
        }

        // rotate
        var rotated = refreshTokenService.rotateSingleSession(refreshToken);
        if (rotated == null) {
            clearRefreshCookie(response); // 안전하게 쿠키도 날려버리자
            return ResponseEntity.status(401).body(Map.of(
                    "status", "ERROR",
                    "message", "리프레시 토큰이 유효하지 않습니다."
            ));
        }

        // 새 refresh 쿠키 세팅
        setRefreshCookie(response, rotated.token(), rotated.ttlSeconds());

        // 새 access 발급 (role/userId는 rotate 과정에서 payload로 이미 검증)
        var payload = refreshTokenService.verify(rotated.token()); // 토큰 발급 직후라 항상 존재
        String subject = String.valueOf(payload.userId());
        String accessToken = accessTokenService.issueAccessToken(subject, payload.role());

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "role", payload.role().name(),
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "expiresIn", accessTokenService.accessTtlSeconds()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, REFRESH_COOKIE_NAME);

        // Redis 토큰 정리
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.logoutSingleSession(refreshToken);
        }

        // refresh 쿠키 삭제(Set-Cookie)
        clearRefreshCookie(response);

        // 성공 응답 (idempotent)
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "로그아웃 완료"
        ));
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken, long ttlSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false)      // 운영은 true(HTTPS)
                .sameSite("Lax")    // 프론트/백 도메인 분리 + 크로스사이트면 None+Secure 고려
                .path(REFRESH_COOKIE_PATH)
                .maxAge(ttlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH) // 발급할 때 path와 반드시 동일해야 삭제됨
                .maxAge(0) // 즉시 만료(삭제)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
