package com.ssafy.keeping.domain.store.service;

import com.ssafy.keeping.domain.menuCategory.service.MenuCategoryService;
import com.ssafy.keeping.domain.store.constant.StoreStatus;
import com.ssafy.keeping.domain.store.dto.StoreEditRequestDto;
import com.ssafy.keeping.domain.store.dto.StorePublicDto;
import com.ssafy.keeping.domain.store.dto.StoreRequestDto;
import com.ssafy.keeping.domain.store.dto.StoreResponseDto;
import com.ssafy.keeping.domain.store.model.Store;
import com.ssafy.keeping.domain.store.repository.StoreRepository;
import com.ssafy.keeping.domain.user.finopenapi.dto.DetailDto;
import com.ssafy.keeping.domain.user.finopenapi.dto.InsertMerchantResponse;
import com.ssafy.keeping.domain.user.owner.model.Owner;
import com.ssafy.keeping.domain.user.owner.repository.OwnerRepository;
import com.ssafy.keeping.domain.wallet.model.WalletStoreBalance;
import com.ssafy.keeping.domain.wallet.repository.WalletStoreBalanceRepository;
import com.ssafy.keeping.global.client.FinOpenApiClient;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import com.ssafy.keeping.global.s3.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository storeRepository;
    private final OwnerRepository ownerRepository;
    private final WalletStoreBalanceRepository balanceRepository;
    private final FinOpenApiClient apiClient;
    private final ImageService imageService;

    /*
     * ==================================
     * 가게 주인(owner) role api 에서 사용할 service 로직
     * ==================================
     * */
    public StoreResponseDto createStore(Long ownerId, StoreRequestDto requestDto) {
        Owner owner = validOwner(ownerId);
        String taxIdNumber = requestDto.getTaxIdNumber();
        String address = requestDto.getAddress();

        boolean exists = storeRepository.existsByTaxIdNumberAndAddress(taxIdNumber, address);
        if (exists) {
            throw new CustomException(ErrorCode.STORE_ALREADY_EXISTS);
        }

        // merchantId 생성
        InsertMerchantResponse response = apiClient.insertMerchant(requestDto.getStoreName());
        if(response == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        String merchantId = getMerchantId(response, requestDto.getStoreName());
        if(merchantId == null || merchantId.isEmpty()) {
            throw new CustomException(ErrorCode.MERCHANTID_NOT_FOUND);
        }


        // 이미지 파일
        String imgUrl = imageService.uploadImage((requestDto.getImgFile()), "store");
        if(imgUrl == null || imgUrl.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return StoreResponseDto.fromEntity(
                storeRepository.save(
                        Store.builder()
                                .owner(owner)
                                .taxIdNumber(requestDto.getTaxIdNumber())
                                .storeName(requestDto.getStoreName())
                                .address(requestDto.getAddress())
                                .phoneNumber(requestDto.getPhoneNumber())
                                .merchantId(Long.valueOf(merchantId))
                                .category(requestDto.getCategory())
                                .bankAccount(requestDto.getBankAccount())
                                .description(requestDto.getDescription())
                                .storeStatus(StoreStatus.ACTIVE)
                                .imgUrl(imgUrl)
                                .build()
                )
        );
    }

    public static String makeImgUrl(MultipartFile file) {
        return "random_img_url";
    }

    @Transactional
    public StoreResponseDto editStore(Long storeId, Long ownerId, StoreEditRequestDto requestDto) {
        Owner owner = validOwner(ownerId);
        Store store = validStore(storeId);
        if (!store.getOwner().getOwnerId().equals(owner.getOwnerId()))
            throw new CustomException(ErrorCode.OWNER_NOT_MATCH);

        if (!Objects.equals(store.getStoreStatus(), StoreStatus.ACTIVE)) {
            throw new CustomException(ErrorCode.STORE_INVALID); // 승인 상태일때만 edit 허용
        }

        String editImgUrl = imageService.updateProfileImage(store.getImgUrl(), requestDto.getImgFile());
        if(editImgUrl == null || editImgUrl.isEmpty()) {
            throw new CustomException(ErrorCode.IMAGE_UPDATE_ERROR);
        }

        String taxId = store.getTaxIdNumber();
        String address = requestDto.getAddress();

        boolean exists = storeRepository.existsByTaxIdNumberAndAddress(taxId, address);
        if (exists) {
            throw new CustomException(ErrorCode.STORE_ALREADY_EXISTS);
        }

        store.patchStore(requestDto, editImgUrl);

        return StoreResponseDto.fromEntity(
                storeRepository.save(store)
        );
    }

    @Transactional
    public StoreResponseDto deleteStore(Long storeId, Long ownerId) {
        Owner owner = validOwner(ownerId);
        Store store = validStore(storeId);

        if (!store.getOwner().getOwnerId().equals(owner.getOwnerId()))
            throw new CustomException(ErrorCode.OWNER_NOT_MATCH);

        boolean hasPositive = balanceRepository
                .existsPositiveBalanceForStoreWithLock(storeId); // 아래 쿼리 참조

        StoreStatus status = hasPositive ? StoreStatus.SUSPENDED : StoreStatus.DELETED;
        store.deleteStore(status);

        return StoreResponseDto.fromEntity(
                storeRepository.save(store)
        );
    }

    /*
     * ==================================
     *  일반 고객 api 에서 사용할 service 로직
     * ==================================
     * */
    public List<StorePublicDto> getAllStore() {
        List<StorePublicDto> allApprovedStoreDto =
                storeRepository.findPublicAllApprovedStore(StoreStatus.ACTIVE);
        return allApprovedStoreDto;
    }

    public StorePublicDto getStoreByStoreId(Long storeId) {
        return storeRepository.findPublicById(storeId, StoreStatus.ACTIVE).orElseThrow(
                () -> new CustomException(ErrorCode.STORE_NOT_FOUND));
    }

    public List<StorePublicDto> getAllStoreByCategory(String categoryName) {
        String category = categoryName == null ? "" : categoryName.trim();
        if (category.isEmpty()) {
            // 카테고리가 비어있으면 전체 조회
            return storeRepository.findPublicAllApprovedStore(StoreStatus.ACTIVE);
        }

        List<StorePublicDto> storesByCategory
                = storeRepository.findPublicAllByCategory(category, StoreStatus.ACTIVE);

        return storesByCategory;
    }

    public List<StorePublicDto> getStoreByStoreName(String storeName) {
        String name = storeName == null ? "" : storeName.trim();
        if (name.isEmpty()) {
            // 이름이 비어있으면 전체 조회
            return storeRepository.findPublicAllApprovedStore(StoreStatus.ACTIVE);
        }
        name = name.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");

        List<StorePublicDto> similarityByNameStoreDto
                = storeRepository.findPublicAllSimilarityByName(name, StoreStatus.ACTIVE);

        return similarityByNameStoreDto;
    }

    private Owner validOwner(Long ownerId) {
        return ownerRepository.findById(ownerId).orElseThrow(
                () -> new CustomException(ErrorCode.OWNER_NOT_FOUND)
        );
    }
    private Store validStore(Long storeId) {
        return storeRepository.findById(storeId).orElseThrow(
                () -> new CustomException(ErrorCode.STORE_NOT_FOUND)
        );
    }
    private String getMerchantId(InsertMerchantResponse response, String requestedMerchantName) {
        if (response == null || response.getREC() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return response.getREC().stream()
                .filter(detail -> requestedMerchantName.equals(detail.getMerchantName()))
                .map(DetailDto::getMerchantId)
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }

    /**
     * 점주의 모든 매장 조회
     */
    public List<StoreResponseDto> getMyStores(Long ownerId) {
        // 점주 유효성 검증
        validOwner(ownerId);

        // 점주의 모든 매장 조회 (삭제되지 않은 매장만)
        List<Store> stores = storeRepository.findByOwnerOwnerIdAndDeletedAtIsNull(ownerId);

        // Store 엔터티를 StoreResponseDto로 변환
        return stores.stream()
                .map(StoreResponseDto::fromEntity)
                .toList();
    }
}
