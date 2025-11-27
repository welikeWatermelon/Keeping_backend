package com.ssafy.keeping.domain.auth.service;

import com.ssafy.keeping.domain.auth.Util.AuthUtil;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoService {

    private final RestTemplate restTemplate;
    private final AuthUtil authUtil;

    /**
     * 카카오 로그아웃 URL 생성
     */
    public String buildKakaoLogoutUrl() {
        return UriComponentsBuilder
                .fromUriString(authUtil.getLogout())
                .queryParam("client_id", authUtil.getRestApiKey())
                .queryParam("logout_redirect_uri", authUtil.getLogoutRedirect())
                .build()
                .toUriString();
    }
}
