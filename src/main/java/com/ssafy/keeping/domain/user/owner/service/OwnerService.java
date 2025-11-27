package com.ssafy.keeping.domain.user.owner.service;

import com.ssafy.keeping.domain.user.finopenapi.dto.*;
import com.ssafy.keeping.domain.user.dto.ProfileUploadResponse;
import com.ssafy.keeping.domain.user.owner.model.Owner;
import com.ssafy.keeping.domain.user.owner.repository.OwnerRepository;
import com.ssafy.keeping.domain.otp.session.RegSession;
import com.ssafy.keeping.domain.otp.session.RegSessionStore;
import com.ssafy.keeping.domain.otp.session.RegStep;
import com.ssafy.keeping.domain.user.owner.dto.OwnerRegisterRequest;
import com.ssafy.keeping.domain.user.owner.dto.OwnerRegisterResponse;
import com.ssafy.keeping.global.s3.service.ImageService;
import com.ssafy.keeping.global.client.FinOpenApiClient;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
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
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final RegSessionStore sessionStore;
    private final ImageService imageService;
    private final FinOpenApiClient apiClient;
    private final SecureRandom secureRandom;

    private static final String SIGN_UP_INFO_KEY = "signup:info:";

    // 고객 등록
    @Transactional
    public OwnerRegisterResponse RegisterOwner(OwnerRegisterRequest dto) {
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
            String email = prefix + "@keeping509owner.com";
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

        // 점주 생성
        Owner owner = Owner.builder()
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
            owner = ownerRepository.save(owner);
        } catch (DataIntegrityViolationException e){
            // TODO: 예외처리
            throw e;
        }

        // 정산용 계좌 생성
        String accountNo = null;

        try{
            log.debug("계좌 생성 시도");
            String role = "OWNER";
            CreateAccountResponse accountResponse = apiClient.createAccount(userKey, role);
            accountNo = accountResponse.getRecResponse().getAccountNo();
            log.debug("계좌 생성 성공 : {}", accountNo);

        } catch (CustomException e) {
            log.debug("해당 계좌 생성 실패 : {}", accountNo);

            throw e;
        }


        // 세션 만료
        sessionStore.deleteSession(SIGN_UP_INFO_KEY, dto.getRegSessionId());
        return OwnerRegisterResponse.register(owner);
    }


    // 프로필 이미지 변경
    @Transactional
    public ProfileUploadResponse uploadProfileImage(Long ownerId, MultipartFile newImage) {
        String oldImgUrl = ownerRepository.findImageUrlByOwnerId(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

        // 변경
        try {
            String newImgUrl = imageService.updateProfileImage(oldImgUrl, newImage);
            ownerRepository.updateImageUrl(ownerId, newImgUrl);

            log.info("사용자 {} 프로필 이미지 업데이트: {}", ownerId, newImgUrl);
            return ProfileUploadResponse.builder()
                    .newImgUrl(newImgUrl)
                    .build();

        } catch (Exception e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    public InsertMerchantResponse insertMerchant(String merchantName) {
        try {
            log.debug("가맹점 등록 시도");
            InsertMerchantResponse response = apiClient.insertMerchant(merchantName);
            log.debug("생성된 merchantId : {}", response.getREC().get(response.getREC().size()-1).getMerchantId());
            return response;

        } catch (CustomException e) {
            log.debug("가맹점 등록 실패");
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private String createEmailId() {
        log.debug("createEmailId > ... ");

        long number = 100000000000000000L + secureRandom.nextLong(900000000000000000L);
        log.debug("createEmailId: {}", number);

        return String.valueOf(number);
    }
}
