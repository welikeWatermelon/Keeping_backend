package com.ssafy.keeping.domain.auth.Util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AuthUtil {

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String redirect;

    @Value("${spring.security.oauth2.client.provider.kakao.logout-uri}")
    private String logout;

    @Value("${spring.security.oauth2.client.registration.kakao.logout-redirect-uri}")
    private String logoutRedirect;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String restApiKey;
}
