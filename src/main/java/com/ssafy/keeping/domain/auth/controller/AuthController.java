package com.ssafy.keeping.domain.auth.controller;

import com.ssafy.keeping.domain.auth.Util.CookieUtil;
import com.ssafy.keeping.domain.auth.enums.UserRole;
import com.ssafy.keeping.domain.auth.security.JwtProvider;
import com.ssafy.keeping.domain.auth.service.AuthService;
import com.ssafy.keeping.domain.auth.service.KakaoService;
import com.ssafy.keeping.domain.auth.service.TokenResponse;
import com.ssafy.keeping.domain.auth.service.TokenService;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterRequest;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterResponse;
import com.ssafy.keeping.domain.user.customer.dto.SignupCustomerResponse;
import com.ssafy.keeping.domain.user.customer.service.CustomerService;
import com.ssafy.keeping.domain.user.dto.UserProfile;
import com.ssafy.keeping.domain.user.owner.dto.OwnerRegisterRequest;
import com.ssafy.keeping.domain.user.owner.dto.OwnerRegisterResponse;
import com.ssafy.keeping.domain.user.owner.dto.SignupOwnerResponse;
import com.ssafy.keeping.domain.user.owner.service.OwnerService;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.Cookie;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final StringRedisTemplate redis;
    private final AuthService authService;
    private final CustomerService customerService;
    private final OwnerService ownerService;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;
    private final TokenService tokenService;
    private final KakaoService kakaoService;

    @GetMapping("/kakao/customer")
    public void kakaoLoginAsCustomer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // redis에 role 저장
        System.out.println("[AUTH CONTROLLER] Saved role=CUSTOMER to session: " + request.getSession().getId());
        redis.opsForValue().set("oauth:role:" + request.getSession().getId(), "CUSTOMER");

        response.sendRedirect("/api/oauth2/authorization/kakao");
    }

    @GetMapping("/kakao/owner")
    public void kakaoLoginAsOwner(HttpServletRequest request,HttpServletResponse response) throws IOException {
        // redis에 role 저장
        System.out.println("[AUTH CONTROLLER] Saved role=CUSTOMER to session: " + request.getSession().getId());
        redis.opsForValue().set("oauth:role:" + request.getSession().getId(), "OWNER");

        response.sendRedirect("/api/oauth2/authorization/kakao");
    }


    @PostMapping("/signup/customer")
    public ResponseEntity<ApiResponse<SignupCustomerResponse>> completeCustomer(
            @RequestBody @Valid CustomerRegisterRequest dto,
            HttpServletResponse httpResponse
    ) {
        CustomerRegisterResponse response = customerService.RegisterCustomer(dto);
        SignupCustomerResponse signUpResponse = authService.signUpTokenForCustomer(response, httpResponse);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다", HttpStatus.CREATED.value(), signUpResponse));
    }

    @PostMapping("/signup/owner")
    public ResponseEntity<ApiResponse<SignupOwnerResponse>> completeOwner(
            @RequestBody @Valid OwnerRegisterRequest dto,
            HttpServletResponse httpResponse
    ) {
        OwnerRegisterResponse response = ownerService.RegisterOwner(dto);
        SignupOwnerResponse signUpResponse = authService.signUpTokenForOwner(response, httpResponse);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다", HttpStatus.CREATED.value(), signUpResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);

            if(refreshToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("refreshToken 이 없습니다", HttpStatus.UNAUTHORIZED.value()));
            }

            if(!jwtProvider.validateToken(refreshToken)) {
                cookieUtil.removeRefreshTokenFromCookie(response);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 refreshToken 입니다.", HttpStatus.UNAUTHORIZED.value()));
            }

            // 현재 정보 추출
            Long userId = jwtProvider.getUserIdFromRefresh(refreshToken);
            UserRole role = jwtProvider.getUserRole(refreshToken);

            TokenResponse newTokens = tokenService.reissueToken(userId, refreshToken, role);

            cookieUtil.addHttpOnlyRefreshCookie(response, newTokens.getRefreshToken(), Duration.ofDays(7));

            return ResponseEntity.ok()
                    .body(ApiResponse.success("토큰 갱신", HttpStatus.OK.value(), newTokens.withoutRefreshToken()));

        } catch (Exception e) {
            cookieUtil.removeRefreshTokenFromCookie(response);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("토큰 갱신 실패 ", HttpStatus.UNAUTHORIZED.value()));
        }
    }

    @GetMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        try {
            String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);
            String accessToken = getJwtFromRequest(request);

            // 1. 우리 서비스 로그아웃 처리
            if(refreshToken != null && jwtProvider.validateToken(refreshToken)) {
                Long userId = jwtProvider.getUserIdFromRefresh(refreshToken);
                UserRole role = jwtProvider.getUserRole(refreshToken);
                String key = "auth:rt:" + role + ":" + userId;
                redis.delete(key);

                // 액세스 토큰도 블랙리스트에 추가 (토큰 만료까지)
                if(accessToken != null && jwtProvider.validateToken(accessToken)) {
                    String atKey = "auth:blacklist:" + accessToken;
                    Date expiration = jwtProvider.getExpirationDate(accessToken);

                    // 현재 시간부터 토큰 만료까지의 남은 시간 계산
                    long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                    if(ttlSeconds > 0) {
                        // TTL과 함께 저장
                        redis.opsForValue().set(atKey, "blacklisted", Duration.ofSeconds(ttlSeconds));
                    }
                }
            }
            cookieUtil.removeRefreshTokenFromCookie(response);

            // 2. 카카오 로그아웃 URL을 프론트엔드에 전달
            String kakaoLogoutUrl = kakaoService.buildKakaoLogoutUrl();

            Map<String, String> result = new HashMap<>();
            result.put("kakaoLogoutUrl", kakaoLogoutUrl);
            result.put("message", "로그아웃 성공");

            return ResponseEntity.ok()
                    .body(ApiResponse.success("로그아웃 성공", HttpStatus.OK.value(), result));

        } catch (Exception e) {
            cookieUtil.removeRefreshTokenFromCookie(response);

            // 에러 시에도 카카오 로그아웃 URL은 제공
            String kakaoLogoutUrl = kakaoService.buildKakaoLogoutUrl();
            Map<String, String> result = new HashMap<>();
            result.put("kakaoLogoutUrl", kakaoLogoutUrl);
            result.put("message", "로그아웃 완료");

            return ResponseEntity.ok()
                    .body(ApiResponse.success("로그아웃 완료", HttpStatus.OK.value(), result));
        }
    }

//    @GetMapping("/logout")
//    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response,
//                                                    Authentication authentication) {
//        try {
//            String refreshToken = cookieUtil.getRefreshTokenFromCookie(request);
//
//            if(refreshToken != null && jwtProvider.validateToken(refreshToken)) {
//                Long userId = jwtProvider.getUserIdFromRefresh(refreshToken);
//                UserRole role = jwtProvider.getUserRole(refreshToken);
//                String key = "auth:rt:" + role + ":" + userId;
//                redis.delete(key);
//
//                // 카카오계정 로그아웃 (일시적으로 비활성화 - URI 등록 필요)
//                System.out.println("카카오 로그아웃 시도");
//                try {
//                    kakaoService.kakaoLogout();
//                } catch (Exception e) {
//                    System.out.println("카카오 로그아웃 실패, 우리 서비스만 로그아웃 진행: " + e.getMessage());
//                    // 카카오 로그아웃 실패해도 우리 서비스 로그아웃은 계속 진행
//                }
//            }
//            cookieUtil.removeRefreshTokenFromCookie(response);
//
//            return ResponseEntity.ok()
//                    .body(ApiResponse.success("로그아웃 성공", HttpStatus.OK.value(), null));
//
//        } catch (Exception e) {
//            cookieUtil.removeRefreshTokenFromCookie(response);
//
//            return ResponseEntity.ok()
//                    .body(ApiResponse.success("로그아웃 완료", HttpStatus.OK.value(), null));
//        }
//    }

    // 현재 사용자 검색
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfile>> getCurrentUser(Authentication authentication) {

        if(authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증 토큰이 필요합니다.", HttpStatus.UNAUTHORIZED.value()));
        }

        Long userId = (Long) authentication.getPrincipal();
        String roleStr = authentication.getAuthorities().iterator().next().getAuthority().substring(5);
        UserRole role = UserRole.valueOf(roleStr);

        UserProfile currentUser = authService.getCurrentUserProfile(userId, role);

        return ResponseEntity.ok()
                .body(ApiResponse.success("사용자 정보 조회 성공", HttpStatus.OK.value(), currentUser));

    }


    // 현재는 페이지가 없어서 /auth/select-role 활용
    @GetMapping("/select-role")
    public String selectRole() {
        return """
                <html>
                <head>
                    <title>역할 선택</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 50px; }
                        .button { 
                            display: inline-block; 
                            padding: 15px 30px; 
                            margin: 10px; 
                            background-color: #007bff; 
                            color: white; 
                            text-decoration: none; 
                            border-radius: 5px; 
                        }
                        .button:hover { background-color: #0056b3; }
                    </style>
                </head>
                <body>
                    <h2>카카오 로그인 - 역할을 선택하세요</h2>
                    <p>아래 버튼 중 하나를 클릭하여 로그인하세요:</p>
                    
                    <a href="/auth/kakao/customer" class="button">🛒 고객으로 로그인</a>
                    <br><br>
                    <a href="/auth/kakao/owner" class="button">🏪 점주로 로그인</a>
                    
                    <hr>
                    <h3>디버깅 정보:</h3>
                    <p><a href="/auth/debug/redis">Redis 상태 확인</a></p>
                </body>
                </html>
                """;
    }

    @GetMapping("/debug/redis")
    public String debugRedis() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>Redis 전체 Keys:</h3>");

        try {
            // 모든 키 조회
            var allKeys = redis.keys("*");
            if (allKeys.isEmpty()) {
                sb.append("<p>Redis에 저장된 키가 없습니다.</p>");
            } else {
                sb.append("<p>총 ").append(allKeys.size()).append("개의 키가 있습니다.</p>");

                // 키를 패턴별로 분류해서 보여주기
                var oauthKeys = allKeys.stream().filter(key -> key.startsWith("oauth:")).toList();
                var signupKeys = allKeys.stream().filter(key -> key.startsWith("signup:")).toList();
                var otpKeys = allKeys.stream().filter(key -> key.startsWith("otp:")).toList();
                var otherKeys = allKeys.stream().filter(key ->
                        !key.startsWith("oauth:") &&
                                !key.startsWith("signup:") &&
                                !key.startsWith("otp:")
                ).toList();

                // OAuth 관련 키들
                if (!oauthKeys.isEmpty()) {
                    sb.append("<h4>OAuth State Keys:</h4>");
                    for (String key : oauthKeys) {
                        String value = redis.opsForValue().get(key);
                        sb.append("<p><strong>").append(key).append("</strong> = ").append(value).append("</p>");
                    }
                }

                // 회원가입 관련 키들
                if (!signupKeys.isEmpty()) {
                    sb.append("<h4>Signup Info Keys:</h4>");
                    for (String key : signupKeys) {
                        String value = redis.opsForValue().get(key);
                        sb.append("<div style='border: 1px solid #ccc; margin: 10px; padding: 10px;'>");
                        sb.append("<strong>").append(key).append("</strong><br>");
                        sb.append("<pre>").append(value).append("</pre>");
                        sb.append("</div>");
                    }
                }

                // OTP 관련 키들
                if (!otpKeys.isEmpty()) {
                    sb.append("<h4>OTP Keys:</h4>");
                    for (String key : otpKeys) {
                        String value = redis.opsForValue().get(key);
                        sb.append("<p><strong>").append(key).append("</strong> = ").append(value).append("</p>");
                    }
                }

                // 기타 키들
                if (!otherKeys.isEmpty()) {
                    sb.append("<h4>Other Keys:</h4>");
                    for (String key : otherKeys) {
                        String value = redis.opsForValue().get(key);
                        sb.append("<p><strong>").append(key).append("</strong> = ").append(value).append("</p>");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("<p>Error: ").append(e.getMessage()).append("</p>");
        }

        sb.append("<hr>");
        sb.append("<p><a href='/auth/debug/clear-redis'>OAuth keys 삭제</a></p>");
        sb.append("<p><a href='/auth/select-role'>Back to role selection</a></p>");

        return sb.toString();
    }

    @GetMapping("/debug/clear-redis")
    public String clearRedis() {
        try {
            var keys = redis.keys("oauth:state:*");
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
            return "<p>OAuth state keys cleared!</p><a href='/auth/select-role'>Back to role selection</a>";
        } catch (Exception e) {
            return "<p>Error: " + e.getMessage() + "</p>";
        }
    }

    @GetMapping("/session-info")
    public ResponseEntity<ApiResponse<String>> getRegSessionId(HttpServletRequest request) {
        System.out.println("[DEBUG] /auth/session-info called");

        // 모든 쿠키 출력
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                System.out.println("[DEBUG] Cookie found: " + cookie.getName() + " = " + cookie.getValue());
            }
        } else {
            System.out.println("[DEBUG] No cookies found in request");
        }

        String regSessionId = cookieUtil.getRegSessionIdFromCookie(request);
        System.out.println("[DEBUG] Extracted regSessionId: " + regSessionId);

        if (regSessionId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("세션 정보를 찾을 수 없습니다", 404));
        }

        return ResponseEntity.ok(ApiResponse.success("세션 정보 조회 성공", 200, regSessionId));
    }

    private final static String AUTHORIZATION_HEADER = "Authorization";
    private final static String BEARER_PREFIX = "Bearer";

    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. Authorization 헤더에서 확인
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }

        // 2. 쿠키에서 확인
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName()) || "newAccessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
