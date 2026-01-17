package com.ssafy.keeping.domain.user.owner.service;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.signup.dto.OwnerSignupRequest;
import com.ssafy.keeping.domain.auth.signup.ticket.SignupTicketPayload;
import com.ssafy.keeping.domain.user.dto.ProfileUploadResponse;
import com.ssafy.keeping.domain.user.owner.model.Owner;
import com.ssafy.keeping.domain.user.owner.repository.OwnerRepository;

import java.util.Optional;

import com.ssafy.keeping.global.s3.service.ImageService;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final ImageService imageService;

    @Transactional
    public Owner registerOwner(OwnerSignupRequest request, SignupTicketPayload payload) {

        Owner owner = Owner.builder()
                .providerType(payload.providerType())
                .providerId(payload.providerId())
                .name(request.name())
                .email(request.email())
                .gender(request.gender())
                .birth(request.birth())
                .imgUrl(payload.profileUrl())
                .phoneNumber(request.phoneNumber())
                .build();

        return ownerRepository.save(owner);
    }

//    /**
//     * OAuth 인증으로 점주 생성 (OTP 없이 즉시 등록)
//     * - 카카오 정보만으로 Owner 생성
//     * - 포인트는 0으로 초기화
//     */
//    @Transactional
//    public Owner createOwnerFromOAuth(String providerId,
//                                     AuthProvider provider,
//                                     String email,
//                                     String imgUrl,
//                                     String nickname) {
//        // 카카오 닉네임을 name으로 사용, 없으면 기본값
//        String name = (nickname != null && !nickname.isEmpty()) ? nickname : "카카오 사용자";
//
//        // Owner 생성 (phone, birth, gender는 NULL)
//        Owner owner = Owner.builder()
//                .providerType(provider)
//                .providerId(providerId)
//                .email(email)
//                .name(name)
//                .imgUrl(imgUrl)
//                .phoneNumber(null)
//                .birth(null)
//                .gender(null)
//                .points(0L)  // 초기 포인트 0
//                .build();
//
//        try {
//            owner = ownerRepository.save(owner);
//            log.info("OAuth로 점주 등록 완료 - ownerId: {}, name: {}, email: {}",
//                    owner.getOwnerId(), owner.getName(), owner.getEmail());
//        } catch (DataIntegrityViolationException e) {
//            log.error("OAuth 점주 등록 실패 - 중복 데이터", e);
//            throw new CustomException(ErrorCode.BAD_REQUEST);
//        }
//
//        return owner;
//    }

    /**
     * 소셜 로그인 제공자 타입과 제공자 ID로 점주 조회
     */
    public Optional<Owner> findByProviderTypeAndProviderId(AuthProvider providerType, String providerId) {
        return ownerRepository.findByProviderTypeAndProviderIdAndDeletedAtIsNull(providerType, providerId);
    }

    /**
     * 프로필 이미지 변경
     */
    @Transactional
    public ProfileUploadResponse uploadProfileImage(Long ownerId, MultipartFile newImage) {
        String oldImgUrl = ownerRepository.findImageUrlByOwnerId(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

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

    /**
     * 점주 포인트 조회
     */
    public Long getOwnerPoints(Long ownerId) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.OWNER_NOT_FOUND));
        return owner.getPoints();
    }

    /**
     * 점주 포인트 추가 (선결제 시 사용)
     */
    @Transactional
    public void addPoints(Long ownerId, Long amount) {
        Owner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.OWNER_NOT_FOUND));
        owner.addPoints(amount);
        ownerRepository.save(owner);
        log.info("점주 포인트 적립 - ownerId: {}, amount: {}, newBalance: {}",
                ownerId, amount, owner.getPoints());
    }
}
