package com.ssafy.keeping.domain.user.owner.controller;

import com.ssafy.keeping.domain.user.dto.ProfileUploadResponse;
import com.ssafy.keeping.domain.user.finopenapi.dto.InsertMerchantResponse;
import com.ssafy.keeping.domain.user.owner.service.OwnerService;
import com.ssafy.keeping.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    // 프로필 이미지 수정
    @PostMapping(value = "/{ownerId}/profile-image/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileUploadResponse>> uploadProfileImage(@PathVariable Long ownerId,
                                                                                 @RequestParam("file") MultipartFile file) {
        ProfileUploadResponse response = ownerService.uploadProfileImage(ownerId, file);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("성공적 변경", HttpStatus.OK.value(),response));

    }

    // test
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<InsertMerchantResponse>> test(@RequestParam("merchantName") String merchantName) {
        InsertMerchantResponse response = ownerService.insertMerchant(merchantName);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("성공", HttpStatus.OK.value(), response));
    }
}
