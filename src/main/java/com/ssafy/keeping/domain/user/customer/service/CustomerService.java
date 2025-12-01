package com.ssafy.keeping.domain.user.customer.service;

import com.ssafy.keeping.domain.auth.pin.service.PinAuthService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterRequest;
import com.ssafy.keeping.domain.user.customer.dto.CustomerRegisterResponse;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileResponse;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileUpdateRequest;
import com.ssafy.keeping.domain.otp.session.RegSession;
import com.ssafy.keeping.domain.otp.session.RegSessionStore;
import com.ssafy.keeping.domain.otp.session.RegStep;
import com.ssafy.keeping.domain.user.dto.ProfileUploadResponse;
import com.ssafy.keeping.global.s3.service.ImageService;
import com.ssafy.keeping.domain.wallet.constant.WalletType;
import com.ssafy.keeping.domain.wallet.model.Wallet;
import com.ssafy.keeping.domain.wallet.repository.WalletRepository;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RegSessionStore sessionStore;
    private final PinAuthService pinAuthService;
    private final WalletRepository walletRepository;
    private final ImageService imageService;

    private static final String SIGN_UP_INFO_KEY = "signup:info:";

    /**
     * 고객 등록 (간소화 버전 - 외부 API 연동 제거)
     */
    @Transactional
    public CustomerRegisterResponse RegisterCustomer(CustomerRegisterRequest dto) {
        RegSession session = sessionStore.getSession(SIGN_UP_INFO_KEY, dto.getRegSessionId());
        if(session.getRegStep() != RegStep.PHONE_VERIFIED) {
            throw new IllegalStateException("휴대폰 인증이 필요합니다.");
        }

        // 고객 생성 (userKey, 계좌, 카드 생성 없이 바로 등록)
        Customer customer = Customer.builder()
                .providerType(session.getProvider())
                .providerId(session.getProviderId())
                .name(session.getName())
                .email(session.getEmail())
                .gender(session.getGender())
                .birth(session.getBirth())
                .imgUrl(session.getImgUrl())
                .phoneNumber(session.getPhoneNumber())
                .build();

        try {
            customer = customerRepository.save(customer);
            log.info("고객 등록 완료 - customerId: {}, name: {}", customer.getCustomerId(), customer.getName());
        } catch (DataIntegrityViolationException e){
            log.error("고객 등록 실패 - 중복 데이터", e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 결제 비밀번호 저장
        pinAuthService.setOrUpdatePin(customer.getCustomerId(), dto.getPaymentPin());

        // 지갑 생성
        Wallet wallet = Wallet.builder().customer(customer).walletType(WalletType.INDIVIDUAL).build();

        try {
            walletRepository.save(wallet);
            log.info("고객 지갑 생성 완료 - customerId: {}", customer.getCustomerId());
        } catch (Exception e) {
            log.error("고객 지갑 생성 실패", e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 세션 만료
        sessionStore.deleteSession(SIGN_UP_INFO_KEY, dto.getRegSessionId());
        return CustomerRegisterResponse.register(customer);
    }

    public Customer validCustomer(Long customerId) {
        return customerRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 프로필 이미지 변경
     */
    @Transactional
    public ProfileUploadResponse uploadProfileImage(Long customerId, MultipartFile newImage) {
        String oldImgUrl = customerRepository.findImageUrlByCustomerId(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

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

    /**
     * 내 프로필 조회
     */
    public CustomerProfileResponse getMyProfile(Long customerId) {
        Customer customer = validCustomer(customerId);

        log.info("고객 프로필 조회 - 고객ID: {}", customerId);
        return CustomerProfileResponse.from(customer);
    }

    /**
     * 내 프로필 수정
     */
    @Transactional
    public CustomerProfileResponse updateMyProfile(Long customerId, CustomerProfileUpdateRequest request) {
        Customer customer = validCustomer(customerId);

        try {
            customerRepository.updateCustomerProfile(customerId, request.getName(), request.getPhoneNumber());

            Customer updatedCustomer = validCustomer(customerId);

            log.info("고객 프로필 수정 완료 - 고객ID: {}, 이름: {}, 전화번호: {}",
                     customerId, request.getName(), request.getPhoneNumber());

            return CustomerProfileResponse.from(updatedCustomer);

        } catch (Exception e) {
            log.error("고객 프로필 수정 실패 - 고객ID: {}", customerId, e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }
}
