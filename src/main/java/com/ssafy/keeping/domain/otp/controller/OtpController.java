package com.ssafy.keeping.domain.otp.controller;

import com.ssafy.keeping.domain.otp.dto.OtpRequest;
import com.ssafy.keeping.domain.otp.dto.OtpRequestResponse;
import com.ssafy.keeping.domain.otp.dto.OtpVerifyRequest;
import com.ssafy.keeping.domain.otp.dto.OtpVerifyResponse;
import com.ssafy.keeping.domain.otp.service.OtpService;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;


    // otp 요청
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<OtpRequestResponse>> request(@RequestBody OtpRequest dto) {
        OtpRequestResponse responseDto = otpService.requestDto(dto);

        return ResponseEntity.ok(ApiResponse.success("OTP가 정상적으로 전송되었습니다", HttpStatus.OK.value(), responseDto));
    }

    // otp 검증
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verify(@Valid @RequestBody OtpVerifyRequest dto) {
        OtpVerifyResponse responseDto = otpService.verifyOtp(dto);
        return ResponseEntity.ok(ApiResponse.success("OTP를 검증합니다.", HttpStatus.OK.value(), responseDto));
    }


}
