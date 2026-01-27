package com.ssafy.keeping.domain.payment.qr.controller;

import com.ssafy.keeping.domain.auth.security.principal.UserPrincipal;
import com.ssafy.keeping.domain.payment.qr.dto.QrCreateRequest;
import com.ssafy.keeping.domain.payment.qr.dto.QrCreateResponse;
import com.ssafy.keeping.domain.payment.qr.service.QrTokenService;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cpqr")
@RequiredArgsConstructor
public class PaymentQrController {

    private final QrTokenService qrTokenService;

    @PostMapping("/new")
    public ResponseEntity<ApiResponse<QrCreateResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody QrCreateRequest req
            ) {
        Long customerId = principal.id();
        QrCreateResponse data = qrTokenService.create(customerId, req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("QR 토큰이 생성되었습니다", HttpStatus.CREATED.value(), data));
    }

}