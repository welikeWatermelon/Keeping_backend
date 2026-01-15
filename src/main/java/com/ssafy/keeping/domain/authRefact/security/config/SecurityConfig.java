package com.ssafy.keeping.domain.authRefact.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
import com.ssafy.keeping.domain.auth.security.JwtAuthenticationFilter;
import com.ssafy.keeping.domain.auth.security.RoleAwareAuthorizationRequestResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${fe.base-url}")
    private String feBaseUrl;

    public static final String[] ALLOW_URLS = {
            "/auth/**",
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

//    private final ClientRegistrationRepository clientRegistrationRepository;
//    private final OAuth2SuccessHandler oAuth2SuccessHandler;
//    private final JwtAuthenticationFilter jwtAuthenticationFilter;
//    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
//    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;













    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            AccessTokenService accessTokenService,
            ObjectMapper objectMapper
    ) {
        return new JwtAuthenticationFilter(accessTokenService, objectMapper);
    }

    @Bean
    public NoStoreAuthResponseFilter noStoreAuthResponseFilter() {
        return new NoStoreAuthResponseFilter();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(feBaseUrl)); // 프론트 도메인
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 쿠키 포함 허용
        config.setMaxAge(3600L);
//        config.setExposedHeaders(Arrays.asList("Authorization", "X-Total-Count")); // 프론트에서 Authorization 헤더를 응답에서 읽어야 하면 필요.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }









    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .oauth2Login(o -> o.authorizationEndpoint(
                        ae -> ae.authorizationRequestResolver(
                                new RoleAwareAuthorizationRequestResolver(redis, clientRegistrationRepository,
                                        "/oauth2/authorization")
                        ))
                        .userInfoEndpoint(ue -> ue.userService(oAuth2ProviderRouter))
                        .successHandler(oAuth2SuccessHandler)
                )

                // CSRF 비활성화 (JWT 사용으로 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)

                // Form 로그인 비활성화
                .formLogin(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 설정 적용

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
                        .requestMatchers("/login/**", "/oauth2/**", "/auth/**", "/favicon.ico").permitAll()
                                       
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                // JWT 인증 필터를 UsernamePasswordAuthenticationFilter 이전에 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
