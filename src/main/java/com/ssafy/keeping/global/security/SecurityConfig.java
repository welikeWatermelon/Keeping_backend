package com.ssafy.keeping.global.security;


import com.ssafy.keeping.domain.auth.handler.OAuth2ProviderRouter;
import com.ssafy.keeping.domain.auth.handler.OAuth2SuccessHandler;
import com.ssafy.keeping.domain.auth.security.JwtAccessDeniedHandler;
import com.ssafy.keeping.domain.auth.security.JwtAuthenticationEntryPoint;
import com.ssafy.keeping.domain.auth.security.JwtAuthenticationFilter;
import com.ssafy.keeping.domain.auth.security.RoleAwareAuthorizationRequestResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    public static final String[] ALLOW_URLS = {
            "/auth/**",
            "/otp/**",
            "/oauth2/**",
            "/login",
            "/error",
            "/actuator/health",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/favicon.ico",
            "/.well-known/**",
            "/s3/**",
            "/owners/stores/**",
            "/api/v1/stores/**",
            "/ocr/*",
            "/customer/register/**",
            "/owners/register/**",
            "/debug/redis"
    };

    public static final String[] TEMP_ALLOW_URLS = {
            "/stores/**", // 통계 API (/stores/{storeId}/statistics/**) 포함
            "/api/**",
            "/api/v1/stores/**",
            "/wallets/**",
            "/owners/*/stores/*/charge-bonus",
            "/owners/*/stores/*/charge-bonus/*",
            "/api/notifications/subscribe/**" // SSE 엔드포인트 명시적 허용
    };

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2ProviderRouter oAuth2ProviderRouter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final StringRedisTemplate redis;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 출처 설정
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        // 또는 특정 도메인만 허용: configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "https://your-domain.com"));

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // 허용할 헤더
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 자격 증명 허용 (쿠키, Authorization 헤더 등)
        configuration.setAllowCredentials(true);

        // 노출할 헤더 (클라이언트에서 접근 가능한 헤더)
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Total-Count"));

        // preflight 요청 캐시 시간 (초)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .oauth2Login(o -> o.authorizationEndpoint(
                        ae -> ae.authorizationRequestResolver(
                                new RoleAwareAuthorizationRequestResolver(redis, clientRegistrationRepository,
                                        "/oauth2/authorization")
                        )).redirectionEndpoint(re -> re.baseUri("/auth/*/callback"))
                        .userInfoEndpoint(ue -> ue.userService(oAuth2ProviderRouter))
                        .successHandler(oAuth2SuccessHandler)
                )

                // CSRF 비활성화 (JWT 사용으로 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)

                // Form 로그인 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                // CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 관리 정책 -> OAuth2 로그인 완료 후 JWT로 전환 되어 현재는 IF_REQUIRED 로 작성
                // TODO:  OAuth2 에서 세션에 role 을 담지 않고 넘겨주는 방식으로 리팩토링 후 수정
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())  // WebSocket iframe 허용
                        .contentTypeOptions(contentTypeOptions -> contentTypeOptions.disable())
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable())  // 개발 환경에서는 비활성화
                )

                // 예외 처리
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // URL별 접근 권한 설정
                // TODO: 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // 인증 없이 접근 가능한 URL, 우선 회원 기능을 추가하고 난 뒤 나누기
                        .requestMatchers(ALLOW_URLS).permitAll()

                        // 임시용
                        .requestMatchers(TEMP_ALLOW_URLS).permitAll()

                        // 가게 조회 (고객용) - 인증 불필요
//                        .requestMatchers("GET", "/stores").permitAll()
//                        .requestMatchers("GET", "/stores/*").permitAll()
//                        .requestMatchers("GET", "/stores/*/menus").permitAll()
//                        .requestMatchers("GET", "/stores/*/menus/categories").permitAll()

                        // 가게 주인만 접근 가능한 URL
                        .requestMatchers("/owners/**").hasRole("OWNER")
//                        .requestMatchers("POST", "/stores").hasRole("OWNER")
//                        .requestMatchers("PATCH", "/stores/*").hasRole("OWNER")
//                        .requestMatchers("DELETE", "/stores/*").hasRole("OWNER")

                        // 메뉴
//                        .requestMatchers("POST", "/stores/*/menus").hasRole("OWNER")
//                        .requestMatchers("PATCH", "/stores/*/menus/*").hasRole("OWNER")
//                        .requestMatchers("DELETE", "/stores/*/menus").hasRole("OWNER")
//                        .requestMatchers("DELETE", "/stores/*/menus/*").hasRole("OWNER")

//                        .requestMatchers("POST", "/stores/*/menus/categories").hasRole("OWNER")
//                        .requestMatchers("PATCH", "/stores/*/menus/categories/*").hasRole("OWNER")
//                        .requestMatchers("DELETE", "/stores/*/menus/categories/*").hasRole("OWNER")

                        .requestMatchers("/cpqr/new").hasRole("CUSTOMER")
                        .requestMatchers("/cpqr/*/initiate").hasRole("OWNER")
                        .requestMatchers("/payments/*/approve").hasRole("CUSTOMER")
                        .requestMatchers("/stores/*/transactions/*/refund").hasRole("OWNER")

                        // customer
                        .requestMatchers("/customers/me/groups").hasRole("CUSTOMER")

                                // 그룹 관리
//                        .requestMatchers("/groups/**").authenticated()

                        // 고객 권한만 필요한 URL
                        .requestMatchers("/customers/**").hasRole("CUSTOMER")
                        .requestMatchers("/groups/**").hasRole("CUSTOMER")

                        // 결제
//                        .requestMatchers("/charge/**", "/payments/**", "/cpqr/**").authenticated()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 이전에 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
