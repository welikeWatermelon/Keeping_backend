package com.ssafy.keeping.domain.otp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.keeping.domain.auth.enums.UserRole;
import com.ssafy.keeping.domain.auth.service.AuthService;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.owner.repository.OwnerRepository;
import com.ssafy.keeping.domain.otp.dto.OtpRequest;
import com.ssafy.keeping.domain.otp.dto.OtpRequestResponse;
import com.ssafy.keeping.domain.otp.dto.OtpVerifyRequest;
import com.ssafy.keeping.domain.otp.dto.OtpVerifyResponse;
import com.ssafy.keeping.domain.otp.session.RegSession;
import com.ssafy.keeping.domain.otp.session.RegSessionStore;
import com.ssafy.keeping.domain.otp.adapter.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final StringRedisTemplate redis;
    private final ObjectMapper om;
    private final CustomerRepository customerRepository;
    private final OwnerRepository ownerRepository;
    private final RegSessionStore sessionStore;
    private final SmsSender smsSender;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final Duration REG_TTL = Duration.ofMinutes(30);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final String OTP_CODE_KEY = "otp:code:";
    private static final String OTP_TRY_KEY = "otp:try:";
    private static final String OTP_KEY_PREFIX = "otp:info:";
    private static final int OTP_MAX_TRIES = 5;

    // OTP 요청
    public OtpRequestResponse requestDto(OtpRequest dto) {

        if(dto.getUserRole() == UserRole.CUSTOMER) {
            // 중복 가입 체크 (핸드폰 번호)
            if(customerRepository.existsByPhoneNumberAndDeletedAtIsNull(dto.getPhoneNumber())) {
                throw new IllegalStateException("이미 가입된 CUSTOMER 계정이 있습니다.");
            }
            // 탈퇴한 사용자라면, 7일 경과했는지 확인
            customerRepository.findByPhoneNumberAndDeletedAtIsNotNullOrderByDeletedAtDesc(dto.getPhoneNumber())
                    .ifPresent(last -> {
                        if(LocalDateTime.now().isBefore(last.getDeletedAt().plusDays(7))) {
                            throw new IllegalStateException("탈퇴 후 7일이 지나야 재가입 가능합니다.");
                        }
                    });
        }

        if(dto.getUserRole() == UserRole.OWNER) {
            if(ownerRepository.existsByPhoneNumberAndDeletedAtIsNull(dto.getPhoneNumber())) {
                throw new IllegalStateException("이미 가입된 OWNER 계정이 있습니다");
            }
            ownerRepository.findByPhoneNumberAndDeletedAtIsNotNullOrderByDeletedAtDesc(dto.getPhoneNumber())
                    .ifPresent(last -> {
                        if(LocalDateTime.now().isBefore(last.getDeletedAt().plusDays(7))) {
                            throw new IllegalStateException("탈퇴 후 7일이 지나야 재가입 가능합니다.");
                        }
                    });
        }

        // 세선 져장
        RegSession session = RegSession.fromOtpRequest(dto, dto.getRegSessionId());
        sessionStore.setSession(OTP_KEY_PREFIX, dto.getRegSessionId(), session, REG_TTL);

        // OTP 전송
        String otp = createNumberKey();

        redis.opsForValue().set(OTP_CODE_KEY + dto.getRegSessionId(), otp, OTP_TTL);
        redis.opsForValue().set(OTP_TRY_KEY + dto.getRegSessionId(), "0", OTP_TTL);

        String text = "[keeping] 본인인증 인증번호는 " + otp + " 입니다. 정확히 입력해주세요.";

        smsSender.send(dto.getPhoneNumber(), text);

        return new OtpRequestResponse(dto.getRegSessionId(), otp);
    }

    // OTP 검증
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest dto) {
        System.out.println("[OTP VERIFY] Received regSessionId: " + dto.getRegSessionId());
        System.out.println("[OTP VERIFY] Looking for key: " + OTP_KEY_PREFIX + dto.getRegSessionId());

        RegSession regSession = sessionStore.getSession(OTP_KEY_PREFIX, dto.getRegSessionId());

        // OTP 검증
        String keyCode = OTP_CODE_KEY + dto.getRegSessionId();
        String keyTry = OTP_TRY_KEY + dto.getRegSessionId();

        String savedCode = redis.opsForValue().get(keyCode);
        if(savedCode == null || savedCode.isBlank()) {
            throw new IllegalStateException("인증이 만료되어 인증에 실패했습니다.");
        }

        // 실패 횟수 확인
        String triesStr = redis.opsForValue().get(keyTry);

        int tries = 0;
        if (triesStr != null) {
            try {
                tries = Integer.parseInt(triesStr);
            } catch (NumberFormatException e) {
                tries = 0; // 파싱 실패 시 0으로 초기화
            }
        }

        if(tries >= OTP_MAX_TRIES) {
            redis.delete(keyCode);
            redis.delete(keyTry);
            throw new IllegalStateException("인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요");
        }

        // 인증 만료
        Long remains = redis.getExpire(keyCode);
        if(remains == null || remains <= 0) {
            redis.opsForValue().set(keyTry, "0", OTP_TTL);
            redis.delete(keyCode);
            redis.delete(keyTry);
            throw new IllegalStateException("인증이 만료되었습니다. 다시 시도해주세요.");
        }

        // 코드 검증 오류
        if(!savedCode.equals(dto.getCode())) {
            // increment 사용으로 안전하게 증가
            redis.opsForValue().increment(keyTry);
            // 기존 TTL 유지
            redis.expire(keyTry, Duration.ofSeconds(redis.getExpire(keyCode)));
            throw new IllegalStateException("인증번호가 일치하지 않습니다.");
        }

        // 코드 검증 성공
        regSession.markVerifiedAt();
        sessionStore.setSession(OTP_KEY_PREFIX, dto.getRegSessionId(), regSession, sessionStore.remainingTtl(OTP_KEY_PREFIX, dto.getRegSessionId()));

        authService.attachOtpInfo(dto.getRegSessionId());

        redis.delete(keyCode);
        redis.delete(keyTry);
        redis.delete(OTP_KEY_PREFIX + dto.getRegSessionId());

        return new OtpVerifyResponse(true);
    }

    private String createNumberKey() {
        log.debug("createNumberKey > ... ");

        int numberKey = 100000 + secureRandom.nextInt(900000);
        log.debug("numberKey: {}", numberKey);

        return String.valueOf(numberKey);
    }
}
