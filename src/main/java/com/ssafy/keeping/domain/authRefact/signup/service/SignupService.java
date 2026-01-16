//package com.ssafy.keeping.domain.authRefact.signup.service;
//
//import com.ssafy.keeping.domain.auth.pin.service.PinAuthService;
//import com.ssafy.keeping.domain.authRefact.enums.UserRole;
//import com.ssafy.keeping.domain.authRefact.signup.dto.*;
//import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketService;
//import com.ssafy.keeping.domain.authRefact.signup.ticket.SignupTicketPayload;
//import com.ssafy.keeping.domain.authRefact.token.AccessTokenService;
//import com.ssafy.keeping.domain.authRefact.token.RefreshTokenService;
//import com.ssafy.keeping.domain.user.customer.model.Customer;
//import com.ssafy.keeping.domain.user.customer.service.CustomerService;
//import com.ssafy.keeping.domain.user.owner.service.OwnerService;
//import com.ssafy.keeping.domain.wallet.service.WalletService;
//import com.ssafy.keeping.global.exception.CustomException;
//import com.ssafy.keeping.global.exception.constants.ErrorCode;
//import lombok.RequiredArgsConstructor;
//import org.springframework.dao.DataIntegrityViolationException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.transaction.support.TransactionSynchronization;
//import org.springframework.transaction.support.TransactionSynchronizationManager;
//
//@Service
//@RequiredArgsConstructor
//public class SignupService {
//
//    private final SignupTicketService signupTicketService;
//    private final CustomerService customerService;
//    private final OwnerService ownerService;
//    private final AccessTokenService accessTokenService;
//    private final RefreshTokenService refreshTokenService;
//    private final PinAuthService pinAuthService;
//    private final WalletService walletService;
//
//    /**
//     * 손님 회원가입
//     * @param req
//     * @return
//     */
//    @Transactional
//    public IssuedAuthTokens<CustomerRegisterResponse> signupCustomer(CustomerSignupRequest req) {
//        SignupTicketPayload payload = validateAndLoadTicket(req.ticket(), UserRole.CUSTOMER);
//
//        // 혹시 이미 가입된 상태면 그대로 토큰 발급(중복 가입 방지)
//        var existing = customerService.findByProviderTypeAndProviderId(payload.providerType(), payload.providerId());
//        if (existing.isPresent()) {
//            Customer customer = existing.get();
//            CustomerRegisterResponse dto = CustomerRegisterResponse.from(customer);
//            signupTicketService.invalidate(req.ticket()); // 커밋이 없으니까 즉시 폐기
//            return issueSuccessToken(customer.getCustomerId(), payload.role(), dto);
//        }
//
//        // 회원 가입
//        try {
//            // 고객 생성
//            Customer saved = customerService.registerCustomer(req, payload);
//            CustomerRegisterResponse dto = CustomerRegisterResponse.from(saved);
//
//            // 지갑 생성
//            walletService.createOrGetIndividualWallet(saved);
//
//            // 핀 번호 저장
//            pinAuthService.setOrUpdatePin(saved.getCustomerId(), req.pin());
//
//            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
//            return issueSuccessToken(saved.getCustomerId(), payload.role(), dto);
//        } catch (DataIntegrityViolationException e) {
//            // provider 기준 중복(레이스)일 수 있으니 한번 더 조회해서 있으면 성공 처리
//            existing = customerService.findByProviderTypeAndProviderId(payload.providerType(), payload.providerId());
//            if (existing.isPresent()) {
//                Customer c = existing.get();
//                CustomerRegisterResponse dto = CustomerRegisterResponse.from(c);
//                signupTicketService.invalidate(req.ticket());
//                return issueSuccessToken(c.getCustomerId(), payload.role(), dto);
//            }
//
//            // 그 외(이메일/폰 중복 등)는 도메인 에러로 매핑
//            throw new CustomException(ErrorCode.CUSTOMER_DUPLICATE);
//        }
//    }
//
//    /**
//     * 점주 회원가입
//     * @param req
//     * @return
//     */
//    @Transactional
//    public IssuedAuthTokens signupOwner(OwnerSignupRequest req) {
//        SignupTicketPayload payload = validateAndLoadTicket(req.ticket(), UserRole.OWNER);
//
//        // 혹시 이미 가입된 상태면 그대로 토큰 발급(중복 가입 방지)
//        var existing = ownerService.findByProviderTypeAndProviderId(payload.providerType(), payload.providerId());
//        if (existing.isPresent()) {
//            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
//            long userId = existing.get().getOwnerId();
//            return issueSuccessToken(userId, payload.role());
//        }
//
//        // 회원 가입
//        try {
//            Owner saved = ownerRepository.save(toOwner(req, payload));
//            invalidateAfterCommit(req.ticket()); // DB 커밋 성공 이후에 ticket 삭제 예약
//            return issueSuccessToken(saved.getOwnerId(), payload.role());
//        } catch (DataIntegrityViolationException e) {
//            throw new IllegalStateException("Duplicate email/phone/provider", e);
//        }
//    }
//
//    /**
//     * ticket -> payload 로드 + 유효성(존재/role) 검증
//     */
//    private SignupTicketPayload validateAndLoadTicket(String ticket, UserRole expectedRole) {
//        SignupTicketPayload payload = signupTicketService.getPayload(ticket);
//
//        if (payload == null) throw new CustomException(ErrorCode.SIGNUP_TICKET_INVALID);
//        if (payload.role() != expectedRole) throw new CustomException(ErrorCode.SIGNUP_ROLE_MISMATCH);
//
//        return payload;
//    }
//
//    /**
//     * 토큰 발급 + 응답 DTO 생성
//     * access + refresh 모두 발급
//     */
//    private <T> IssuedAuthTokens<T> issueSuccessToken(long userId, UserRole role, T userDto) {
//        String accessToken = accessTokenService.issueAccessToken(String.valueOf(userId), role);
//        var issuedRefresh = refreshTokenService.issueSingleSession(userId, role);
//
//        SignupResponse<T> body = new SignupResponse<>(
//                userDto,
//                AuthResponse.bearer(accessToken, role)
//        );
//
//        return new IssuedAuthTokens<>(body, issuedRefresh.token(), issuedRefresh.ttlSeconds());
//    }
//
//    /**
//     * 현재 트랜잭션에 “커밋 후 실행할 콜백”을 등록
//     * 티켓을 삭제하는 메서드
//     * @param ticket
//     */
//    private void invalidateAfterCommit(String ticket) {
//        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
//            signupTicketService.invalidate(ticket); // 트랜잭션 없으면 즉시 삭제
//            return;
//        }
//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
//            @Override
//            public void afterCommit() {
//                signupTicketService.invalidate(ticket);
//            }
//        });
//    }
//}
