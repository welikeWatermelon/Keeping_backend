package com.ssafy.keeping.domain.user.customer.service;

import com.ssafy.keeping.domain.auth.pin.service.PinAuthService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterRequest;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterResponse;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileResponse;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileUpdateRequest;
import com.ssafy.keeping.domain.user.customer.dto.CustomerCardResponse;
import com.ssafy.keeping.domain.otp.session.RegSession;
import com.ssafy.keeping.domain.otp.session.RegSessionStore;
import com.ssafy.keeping.domain.otp.session.RegStep;
import com.ssafy.keeping.domain.user.dto.ProfileUploadResponse;
import com.ssafy.keeping.domain.user.finopenapi.dto.*;
import com.ssafy.keeping.global.s3.service.ImageService;
import com.ssafy.keeping.domain.wallet.constant.WalletType;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.global.client.FinOpenApiClient;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import com.ssafy.keeping.domain.charge.service.SsafyFinanceApiService;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardInquiryResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardInquiryRecDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RegSessionStore sessionStore;
    private final FinOpenApiClient apiClient;
    private final PinAuthService pinAuthService;
    private final WalletRepository walletRepository;
    private final ImageService imageService;
    private final SsafyFinanceApiService ssafyFinanceApiService;
    private final SecureRandom secureRandom;

    private static final String SIGN_UP_INFO_KEY = "signup:info:";

    // 고객 등록
    @Transactional
    public CustomerRegisterResponse RegisterCustomer(CustomerRegisterRequest dto) {
        RegSession session = sessionStore.getSession(SIGN_UP_INFO_KEY, dto.getRegSessionId());
        if(session.getRegStep() != RegStep.PHONE_VERIFIED) {
            throw new IllegalStateException("휴대폰 인증이 필요합니다.");
        }

        // userKey 생성
        String userKey;

//        try {
//            SearchUserKeyResponseDto searchUserKeyResponse = apiClient.searchUserKey(session.getEmail());
//
//            // userKey 있으면
//            if(searchUserKeyResponse != null
//                    && searchUserKeyResponse.getUserKey() != null
//                    && !searchUserKeyResponse.getUserKey().isEmpty()) {
//
//                userKey = searchUserKeyResponse.getUserKey();
//                log.debug("기존 userKey 사용");
//
//            } else {
//                // userKey 생성 (catch 문으로 이동)
//                log.debug("새로운 userKey 생성");
//                throw new CustomException(ErrorCode.USER_KEY_NOT_FOUND);
//            }
//
//        } catch (Exception e) {
            // userKey 생성
            try {
                String prefix = createEmailId();
                String email = prefix + "@keeping509customer.com";
                log.debug("email : {}, FinOpenApi userkey 생성 : {}", email, session.getEmail());

                InsertMemberResponseDto member = apiClient.insertMember(email);
                userKey = member.getUserKey();
                log.debug("userKey 생성 완료");

            } catch (CustomException ex) {
                // 생성 실패
                log.warn("FinOpenApi Member 생성 실패 : {}", session.getEmail());
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
//        }

        // userKey 가 null 이거나 empty
        if(userKey == null || userKey.isEmpty()) {
            log.error("userKey 얻을 수 없음 : {}", session.getEmail());
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 고객 생성
        Customer customer = Customer.builder()
                .providerType(session.getProvider())
                .providerId(session.getProviderId())
                .name(session.getName())
                .email(session.getEmail())
                .gender(session.getGender())
                .birth(session.getBirth())
                .imgUrl(session.getImgUrl())
                .phoneNumber(session.getPhoneNumber())
                .userKey(userKey)
                .build();

        try {
            customer = customerRepository.save(customer);
        } catch (DataIntegrityViolationException e){
            // TODO: 예외처리
            throw e;
        }

        // 결제 비밀번호 저장
        pinAuthService.setOrUpdatePin(customer.getCustomerId(), dto.getPaymentPin());

        // 계좌 생성
        String accountNo = null;

        try{
            log.debug("계좌 생성 시도");
            String role = "CUSTOMER";
            CreateAccountResponse accountResponse = apiClient.createAccount(userKey, role);
            accountNo = accountResponse.getRecResponse().getAccountNo();
            log.debug("계좌 생성 성공");

        } catch (CustomException e) {
            log.debug("해당 계좌 생성 실패 : {}", accountNo);

            throw e;
        }

        // 카드 생성
        try{
            log.debug("해당 계좌로 카드 생성 : {}", accountNo);
            apiClient.issueCard(userKey, accountNo);
            log.debug("카드 생성 성공");
        } catch (CustomException e) {
            log.debug("해당 계좌로 카드 생성 실패 : {}", accountNo);
            throw e;
        }

        // 계좌 입금 (1억)
        try {
            log.debug("해당 계좌로 입금 : {}", accountNo);
            apiClient.accountDeposit(userKey, accountNo);
            log.debug("입금 성공");

        } catch (CustomException e) {
            log.debug("해당 계좌로 입금 실패 : {}", accountNo);
            throw e;
        }

        // 지갑 생성
        Wallet wallet = Wallet.builder().customer(customer).walletType(WalletType.INDIVIDUAL).build();

        try {
            walletRepository.save(wallet);
        } catch (CustomException e) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        // 세션 만료
        sessionStore.deleteSession(SIGN_UP_INFO_KEY, dto.getRegSessionId());
        return CustomerRegisterResponse.register(customer);
    }

    public Customer validCustomer(Long customerId) {
        return customerRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    // 프로필 이미지 변경
    @Transactional
    public ProfileUploadResponse uploadProfileImage(Long customerId, MultipartFile newImage) {
        String oldImgUrl = customerRepository.findImageUrlByCustomerId(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

        // 변경
        try {
            String newImgUrl = imageService.updateProfileImage(oldImgUrl, newImage);
            customerRepository.updateImageUrl(customerId, newImgUrl);

            log.info("사용자 {} 프로필 이미지 업데이트: {}", customerId, newImgUrl);
            return ProfileUploadResponse.builder()
                    .newImgUrl(newImgUrl)
                    .build();

        } catch (Exception e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    // 내 프로필 조회
    public CustomerProfileResponse getMyProfile(Long customerId) {
        Customer customer = validCustomer(customerId);

        log.info("고객 프로필 조회 - 고객ID: {}", customerId);
        return CustomerProfileResponse.from(customer);
    }

    // 내 프로필 수정
    @Transactional
    public CustomerProfileResponse updateMyProfile(Long customerId, CustomerProfileUpdateRequest request) {
        Customer customer = validCustomer(customerId);

        // 이름과 전화번호만 수정 가능
        try {
            customerRepository.updateCustomerProfile(customerId, request.getName(), request.getPhoneNumber());

            // 업데이트된 정보 다시 조회
            Customer updatedCustomer = validCustomer(customerId);

            log.info("고객 프로필 수정 완료 - 고객ID: {}, 이름: {}, 전화번호: {}",
                     customerId, request.getName(), request.getPhoneNumber());

            return CustomerProfileResponse.from(updatedCustomer);

        } catch (Exception e) {
            log.error("고객 프로필 수정 실패 - 고객ID: {}", customerId, e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    // 내 카드 조회
    public CustomerCardResponse getMyCard(Long customerId) {
        Customer customer = validCustomer(customerId);

        try {
            // SSAFY 금융API로 카드 목록 조회
            SsafyCardInquiryResponseDto response = ssafyFinanceApiService.inquireCreditCardList(customer.getUserKey());

            // 카드 목록에서 첫 번째 카드만 반환 (비즈니스 로직상 카드는 1개만 존재)
            if (response.getREC() != null && !response.getREC().isEmpty()) {
                SsafyCardInquiryRecDto firstCard = response.getREC().get(0);

                log.info("고객 카드 조회 성공 - 고객ID: {}, 카드번호: {}",
                         customerId, firstCard.getCardNo());

                return CustomerCardResponse.from(firstCard);
            } else {
                log.warn("고객 카드 없음 - 고객ID: {}", customerId);
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }

        } catch (Exception e) {
            log.error("고객 카드 조회 실패 - 고객ID: {}", customerId, e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private String createEmailId() {
        log.debug("createEmailId > ... ");

        long number = 100000000000000000L + secureRandom.nextLong(900000000000000000L);
        log.debug("createEmailId: {}", number);

        return String.valueOf(number);
    }

}
