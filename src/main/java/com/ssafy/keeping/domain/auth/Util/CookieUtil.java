package com.ssafy.keeping.domain.auth.Util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    public void addHttpOnlyRefreshCookie(HttpServletResponse response, String refreshToken, Duration ttl) {
        ResponseCookie responseCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(ttl)
                .sameSite("Lax")
                .build();

        response.setHeader("Set-Cookie", responseCookie.toString());
    }

    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        if(request.getCookies() == null) {
            return null;
        }

        for(Cookie cookie : request.getCookies()) {
            if(REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    public void removeRefreshTokenFromCookie(HttpServletResponse response) {
        ResponseCookie responseCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.setHeader("Set-Cookie", responseCookie.toString());
    }

    public String getRegSessionIdFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("regSessionId".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
