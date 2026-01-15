package com.ssafy.keeping.domain.user.customer.service;

import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.pin.service.PinAuthService;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileResponse;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileUpdateRequest;
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

import java.util.Optional;


@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PinAuthService pinAuthService;
    private final WalletRepository walletRepository;
    private final ImageService imageService;

    /**
     * OAuth 인증으로 고객 생성 (OTP 없이 즉시 등록)
     * - 카카오 정보만으로 Customer + Wallet 생성
     * - PIN은 나중에 설정 가능
     */
    @Transactional
    public Customer createCustomerFromOAuth(String providerId,
                                           AuthProvider provider,
                                           String email,
                                           String imgUrl,
                                           String nickname) {
        // 카카오 닉네임을 name으로 사용, 없으면 기본값
        String name = (nickname != null && !nickname.isEmpty()) ? nickname : "카카오 사용자";

        // Customer 생성 (phone, birth, gender는 NULL)
        Customer customer = Customer.builder()
                .providerType(provider)
                .providerId(providerId)
                .email(email)
                .name(name)
                .imgUrl(imgUrl)
                .phoneNumber(null)
                .birth(null)
                .gender(null)
                .build();

        try {
            customer = customerRepository.save(customer);
            log.info("OAuth로 고객 등록 완료 - customerId: {}, name: {}, email: {}",
                    customer.getCustomerId(), customer.getName(), customer.getEmail());
        } catch (DataIntegrityViolationException e) {
            log.error("OAuth 고객 등록 실패 - 중복 데이터", e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // 지갑 생성 (balance는 기본값 0)
        Wallet wallet = Wallet.builder()
                .customer(customer)
                .walletType(WalletType.INDIVIDUAL)
                .build();

        try {
            walletRepository.save(wallet);
            log.info("OAuth 고객 지갑 생성 완료 - customerId: {}", customer.getCustomerId());
        } catch (Exception e) {
            log.error("OAuth 고객 지갑 생성 실패", e);
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // CustomerPinAuth는 나중에 사용자가 직접 설정하도록 생성하지 않음

        return customer;
    }

    public Customer validCustomer(Long customerId) {
        return customerRepository.findByCustomerIdAndDeletedAtIsNull(customerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 소셜 로그인 제공자 타입과 제공자 ID로 고객 조회
     */
    public Optional<Customer> findByProviderTypeAndProviderId(AuthProvider providerType, String providerId) {
        return customerRepository.findByProviderTypeAndProviderIdAndDeletedAtIsNull(providerType, providerId);
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
