package com.ssafy.keeping.domain.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class RoleAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final StringRedisTemplate redis;
    private final ClientRegistrationRepository repo;
    private final String authorizationRequestBaseUri;

    private DefaultOAuth2AuthorizationRequestResolver delegate() {
        return new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest base = delegate().resolve(request);
        return rememberRole(request, base);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest base = delegate().resolve(request, clientRegistrationId);
        return rememberRole(request, base);
    }

    private OAuth2AuthorizationRequest rememberRole(HttpServletRequest request, OAuth2AuthorizationRequest base) {
        if (base == null) return null;

//        String role = request.getParameter("role");
        String role = redis.opsForValue().get("oauth:role:" + request.getSession().getId());
        
        // role이 있으면 Redis에 저장
        if (role != null && base.getState() != null) {
            String key = "oauth:state:" + base.getState();

            redis.opsForValue().set(key, role, Duration.ofMinutes(5));
            System.out.println("[OAUTH] save role=" + role + " state=" + base.getState());
            
            // prompt=login 항상 새로 로그인
            Map<String, Object> additionalParameters = new HashMap<>(base.getAdditionalParameters());
            additionalParameters.put("prompt", "login");
            
            return OAuth2AuthorizationRequest.from(base)
                    .additionalParameters(additionalParameters)
                    .build();
        } else {
            System.out.println("[OAUTH] role or state missing. role=" + role + " state=" + base.getState());
        }
        
        return base;
    }
}
