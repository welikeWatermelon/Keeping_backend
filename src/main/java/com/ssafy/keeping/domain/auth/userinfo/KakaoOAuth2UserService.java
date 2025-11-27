package com.ssafy.keeping.domain.auth.userinfo;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    // 위임
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User rawUser = new DefaultOAuth2UserService().loadUser(userRequest);
        Map<String, Object> attributes = rawUser.getAttributes();

        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        Map<String, Object> profile = (kakaoAccount != null) ? (Map<String, Object>) kakaoAccount.get("profile") : null;

        String email = (kakaoAccount != null) ? (String)kakaoAccount.get("email") : null;
        String imgUrl = (profile != null) ? (String)profile.get("profile_image_url") : null;

        String providerId = String.valueOf(attributes.get("id"));
        AuthProvider provider = AuthProvider.KAKAO;

        Map<String, Object> mapped = new HashMap<>(attributes);
        mapped.put("email", email);
        mapped.put("providerId", providerId);
        mapped.put("provider", provider);
        mapped.put("imgUrl", imgUrl);

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                mapped,
                "id"
        );
    }


}
