package com.ssafy.keeping.domain.charge.controller;

import com.ssafy.keeping.domain.charge.dto.request.PrepaymentRequestDto;
import com.ssafy.keeping.domain.charge.dto.response.PrepaymentResponseDto;
import com.ssafy.keeping.domain.charge.service.PrepaymentService;
import com.ssafy.keeping.domain.idempotency.model.IdempotentResult;
import com.ssafy.keeping.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PrepaymentController {

    private final PrepaymentService prepaymentService;

    /**
     * 선결제 처리 (멱등성 적용)
     * 
     * @param storeId 가게 ID
     * @param idempotencyKey 멱등성 키 헤더
     * @param requestDto 선결제 요청 정보
     * @return 선결제 처리 결과
     */
    @PostMapping("/{storeId}/prepayment")
    public ResponseEntity<ApiResponse<PrepaymentResponseDto>> processPrePayment(
            @PathVariable @Positive(message = "가게 ID는 양수여야 합니다.") Long storeId,
            @AuthenticationPrincipal Long customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PrepaymentRequestDto requestDto) {

        log.info("선결제 요청 수신 - 가게ID: {}, 사용자ID: {}, 금액: {}, 멱등키: {}",
                storeId, customerId, requestDto.getPaymentBalance(), idempotencyKey);

        IdempotentResult<PrepaymentResponseDto> result = prepaymentService.processPayment(storeId, customerId, idempotencyKey, requestDto);
        
        PrepaymentResponseDto responseDto = result.getBody();
        HttpStatus httpStatus = result.getHttpStatus();
        
        String message;
        if (result.isReplay()) {
            message = "이전에 처리된 선결제 결과를 반환합니다.";
            log.info("선결제 재생 응답 - 거래ID: {}", responseDto.getTransactionId());
        } else if (httpStatus == HttpStatus.ACCEPTED) {
            message = "선결제 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.";
            log.info("선결제 처리 중 - Retry-After: {}초", result.getRetryAfterSeconds());
            
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(httpStatus);
            if (result.getRetryAfterSeconds() != null) {
                builder.header("Retry-After", result.getRetryAfterSeconds().toString());
            }
            return builder.body(ApiResponse.success(message, httpStatus.value(), null));
        } else {
            message = "선결제가 성공적으로 완료되었습니다.";
            log.info("선결제 처리 성공 - 거래ID: {}", responseDto.getTransactionId());
        }
        
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.success(message, httpStatus.value(), responseDto));
    }
}