package com.ssafy.keeping.domain.authRefact.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.authRefact.enums.UserRole;
import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketPayload;
import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketPayloadFactory;
import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketService;
import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
import com.ssafy.keeping.domain.authRefact.token.RefreshTokenService;
import com.ssafy.keeping.domain.user.customer.service.CustomerService;
import com.ssafy.keeping.domain.user.owner.service.OwnerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper; // 자바 객체(Map 등)를 JSON 문자열로 바꿔서 response에 쓰기 위함
    private final CustomerService customerService;
    private final OwnerService ownerService;
    private final SignupTicketService signupTicketService; // 미가입일 때 Redis에 티켓 저장하고 ticket(UUID) 발급

    private final AccessTokenService accessTokenService; // accessToken(JWT) 발급
    private final RefreshTokenService refreshTokenService; //  refreshToken(UUID) 발급

    // OAuth2 인증이 성공했을 때 스프링이 호출해주는 메서드
    // authentication 안에 OAuth2 로그인 결과(Principal, registrationId 등)가 들어있다.
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        try {
            boolean wantsHtml = isBrowser(request); // test

            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;

            // application.yml에서 등록한 OAuth2 클라이언트 이름
            String registrationId = token.getAuthorizedClientRegistrationId(); // (kakao-customer / kakao-owner)

            // role 결정
            UserRole role = switch (registrationId) {
                case "kakao-customer" -> UserRole.CUSTOMER;
                case "kakao-owner" -> UserRole.OWNER;
                default -> throw new IllegalStateException("Unknown registrationId: " + registrationId);
            };

            OAuth2User user = token.getPrincipal();
            String providerId = user.getName(); // 카카오 id (user-name-attribute=id 설정)

            AuthProvider providerType = AuthProvider.KAKAO; // 지금은 카카오만 붙이니까 고정

            if (role == UserRole.CUSTOMER) {
                // 회원가입된 사용자가 있는지 조회
                var existing = customerService.findByProviderTypeAndProviderId(providerType, providerId);
                if (existing.isPresent()) { // 있으면 → JWT 발급해서 SUCCESS 응답
                    long userId = existing.get().getCustomerId();
                    respondLoginSuccess(response, userId, role);

                    if (wantsHtml) { // test
                        String subject = String.valueOf(userId);
                        String accessToken = accessTokenService.issueAccessToken(subject, role);
                        redirectToSuccessPage(response, accessToken, role);
                        return;
                    } // test

                    return;
                }
            } else {
                var existing = ownerService.findByProviderTypeAndProviderId(providerType, providerId);
                if (existing.isPresent()) {
                    long userId = existing.get().getOwnerId();
                    respondLoginSuccess(response, userId, role);

                    if (wantsHtml) { // test
                        String subject = String.valueOf(userId);
                        String accessToken = accessTokenService.issueAccessToken(subject, role);
                        redirectToSuccessPage(response, accessToken, role);
                        return;
                    } // test

                    return;
                }
            }

            // 미가입 → ticket 발급
            SignupTicketPayload payload = SignupTicketPayloadFactory.payload(role, providerType, providerId);
            String ticket = signupTicketService.create(payload, Duration.ofMinutes(10));

            if (wantsHtml) { // test
                response.sendRedirect("/test/signup?ticket=" + url(ticket) + "&role=" + url(role.name()));
                return;
            } // test

            writeJson(response, 200, Map.of(
                    "status", "SIGNUP_REQUIRED",
                    "role", role.name(),
                    "ticket", ticket,
                    "expiresIn", 600
            ));
        } catch (Exception e) {
            try {
                writeJson(response, 500, Map.of("status", "ERROR", "message", e.getMessage()));
            } catch (Exception ignored) {}
        }
    }

    private void respondLoginSuccess(HttpServletResponse response, long userId, UserRole role) throws Exception {
        String subject = String.valueOf(userId);

        // 1) access 발급 (JWT)
        String accessToken = accessTokenService.issueAccessToken(subject, role);

        // 2) refresh 발급 (opaque UUID, Redis 저장)
        var issued = refreshTokenService.issueSingleSession(userId, role);

        // 3) refresh를 HttpOnly 쿠키로 내려줌
        setRefreshCookie(response, issued.token(), issued.ttlSeconds());

        // 4) access는 JSON으로 내려줌
        writeJson(response, 200, Map.of(
                "status", "SUCCESS",
                "role", role.name(),
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "expiresIn", accessTokenService.accessTtlSeconds()
        ));
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        // 캐시 방지
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken, long ttlSeconds) {
        ResponseCookie cookie = ResponseCookie.from("REFRESH_TOKEN", refreshToken)
                .httpOnly(true)
                .secure(false)           // true: HTTPS 연결에서만 쿠키를 전송함운영 | false: HTTP에서도 전송됨 (로컬 개발용)
                .sameSite("Lax")         // 보통 Lax 추천 (프론트/백 도메인이 달라서 cross-site 쿠키가 필요하면 SameSite=None + secure(true)로 바꿔야 함)(지금은 Lax라서 cross-site 환경이면 쿠키가 안 붙을 수 있음)
                .path("/api/auth")       // 쿠키가 어떤 경로 요청에 포함될지를 제한
                .maxAge(ttlSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // test
    private boolean isBrowser(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    // test
    private void redirectToSuccessPage(HttpServletResponse response, String jwt, UserRole role) throws Exception {
        // 토큰을 URL fragment(#)에 넣으면 서버 로그/쿼리로 안 넘어가서 테스트에 비교적 안전함
        String fragment = "#accessToken=" + url(jwt) + "&role=" + url(role.name());
        response.sendRedirect("/test/success" + fragment);
    }

    // test
    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

}
/*

OAuth2JsonSuccessHandler 이 핸들러가 동작할 수 있도록
SecurityConfig에

```
.oauth2Login(oauth -> oauth.successHandler(successHandler))
```
기본 동작 대신 내 핸들러가 동작할 수 있도록 등록해주어야 한다.

 */