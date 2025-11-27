package com.ssafy.keeping.domain.store.controller;


import com.ssafy.keeping.domain.menuCategory.dto.MenuCategoryEditRequestDto;
import com.ssafy.keeping.domain.menuCategory.dto.MenuCategoryRequestDto;
import com.ssafy.keeping.domain.menuCategory.dto.MenuCategoryResponseDto;
import com.ssafy.keeping.domain.store.dto.StoreEditRequestDto;
import com.ssafy.keeping.domain.store.dto.StorePublicDto;
import com.ssafy.keeping.domain.store.dto.StoreResponseDto;
import com.ssafy.keeping.domain.store.dto.StoreRequestDto;
import com.ssafy.keeping.domain.store.service.StoreService;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/stores")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    /* =================================
     * 일반 고객이 가게 조회하는 api
     * ==================================
     * */
    @GetMapping(params = {"!name", "!category"})
    public ResponseEntity<ApiResponse<List<StorePublicDto>>> getAllStore() {
        return ResponseEntity.ok(ApiResponse.success("전체 매장이 조회되었습니다", HttpStatus.OK.value(), storeService.getAllStore()));
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StorePublicDto>> getStore(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(ApiResponse.success("해당 store id로 매장이 조회되었습니다.", HttpStatus.OK.value(), storeService.getStoreByStoreId(storeId)));
    }

    @GetMapping(params = "category")
    public ResponseEntity<ApiResponse<List<StorePublicDto>>> getAllStoreByCategory(
            @RequestParam String category
    ) {
        return ResponseEntity.ok(ApiResponse.success("해당 category로 매장이 조회되었습니다.", HttpStatus.OK.value(),
                storeService.getAllStoreByCategory(category)));
    }

    @GetMapping(params = "name")
    public ResponseEntity<ApiResponse<List<StorePublicDto>>> getStore(
            @RequestParam String name
    ) {
        return ResponseEntity.ok(ApiResponse.success("store name으로 매장이 조회되었습니다.", HttpStatus.OK.value(),
                storeService.getStoreByStoreName(name)));
    }
}
