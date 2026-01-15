package com.ssafy.keeping.domain.authRefact.signup.service;

import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketService;
import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
import com.ssafy.keeping.domain.authRefact.token.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final SignupTicketService signupTicketService;
    private final CustomerRepository customerRepository;
    private final OwnerRepository ownerRepository;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public record IssuedAuthTokens(AuthTokenResponse body, String refreshToken, long refreshTtlSeconds) {}

    /**
     * 손님 회원가입
     * @param req
     * @return
     */
    @Transactional
    public IssuedAuthTokens signupCustomer(CustomerSignupRequest req) {
        SignupTicketPayload payload = validateAndLoadTicket(req.ticket(), UserRole.CUSTOMER);

        // 혹시 이미 가입된 상태면 그대로 토큰 발급(중복 가입 방지)
        var existing = customerRepository.findByProviderTypeAndProviderId(payload.providerType(), payload.providerId());
        if (existing.isPresent()) {
            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
            long userId = existing.get().getCustomerId();
            return issueSuccessToken(userId, payload.role());
        }

        // 회원 가입
        try {
            Customer saved = customerRepository.save(toCustomer(req, payload));
            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
            return issueSuccessToken(saved.getCustomerId(), payload.role());
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Duplicate email/phone/provider", e);
        }
    }

    /**
     * 점주 회원가입
     * @param req
     * @return
     */
    @Transactional
    public IssuedAuthTokens signupOwner(OwnerSignupRequest req) {
        SignupTicketPayload payload = validateAndLoadTicket(req.ticket(), UserRole.OWNER);

        // 혹시 이미 가입된 상태면 그대로 토큰 발급(중복 가입 방지)
        var existing = ownerRepository.findByProviderTypeAndProviderId(payload.providerType(), payload.providerId());
        if (existing.isPresent()) {
            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
            long userId = existing.get().getOwnerId();
            return issueSuccessToken(userId, payload.role());
        }

        // 회원 가입
        try {
            Owner saved = ownerRepository.save(toOwner(req, payload));
            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
            return issueSuccessToken(saved.getOwnerId(), payload.role());
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Duplicate email/phone/provider", e);
        }
    }

    /**
     * ticket -> payload 로드 + 유효성(존재/role) 검증
     */
    private SignupTicketPayload validateAndLoadTicket(String ticket, UserRole expectedRole) {
        SignupTicketPayload payload = signupTicketService.getPayload(ticket);

        if (payload == null) throw new CustomException(ErrorCode.SIGNUP_TICKET_INVALID);
        if (payload.role() != expectedRole) throw new CustomException(ErrorCode.SIGNUP_ROLE_MISMATCH);

        return payload;
    }

    /**
     * 토큰 발급 + 응답 DTO 생성
     * access + refresh 모두 발급
     */
    private IssuedAuthTokens issueSuccessToken(long userId, UserRole role) {
        String subject = String.valueOf(userId);
        String accessToken = accessTokenService.issueAccessToken(subject, role);
        var issuedRefresh = refreshTokenService.issueSingleSession(userId, role);

        AuthTokenResponse body = new AuthTokenResponse("SUCCESS", role.name(), accessToken, "Bearer", accessTokenService.accessTtlSeconds());

        return new IssuedAuthTokens(body, issuedRefresh.token(), issuedRefresh.ttlSeconds());
    }

    private Customer toCustomer(CustomerSignupRequest req, SignupTicketPayload payload) {
        return Customer.builder()
                .providerId(payload.providerId())
                .providerType(payload.providerType())
                .phoneNumber(req.phoneNumber())
                .birth(req.birth())
                .name(req.name())
                .gender(req.gender())
                .pin(req.pin())
                .build();
    }

    private Owner toOwner(OwnerSignupRequest req, SignupTicketPayload payload) {
        return Owner.builder()
                .providerId(payload.providerId())
                .providerType(payload.providerType())
                .phoneNumber(req.phoneNumber())
                .birth(req.birth())
                .name(req.name())
                .gender(req.gender())
                .build();
    }

    /**
     * 현재 트랜잭션에 “커밋 후 실행할 콜백”을 등록
     * 티켓을 삭제하는 메서드
     * @param ticket
     */
    private void invalidateAfterCommit(String ticket) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            signupTicketService.invalidate(ticket); // 트랜잭션 없으면 즉시 삭제
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                signupTicketService.invalidate(ticket);
            }
        });
    }
}
