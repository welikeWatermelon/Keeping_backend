package com.ssafy.keeping.domain.payment.intent.controller;

import com.ssafy.keeping.domain.idempotency.model.IdempotentResult;
import com.ssafy.keeping.domain.payment.intent.dto.ApproveRequest;
import com.ssafy.keeping.domain.payment.intent.dto.PaymentIntentDetailResponse;
import com.ssafy.keeping.domain.payment.intent.service.PaymentIntentService;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import com.ssafy.keeping.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentApprovalController {

    private final PaymentIntentService paymentIntentService;

    @Operation(summary = "결제 승인", description = "Idempotency-Key 필수, 승인은 고객 인증이 필요합니다.")
    @PostMapping("/{intentId}/approve")
    public ResponseEntity<ApiResponse<PaymentIntentDetailResponse>> approve(
            @PathVariable("intentId")UUID intentId,
            @RequestHeader("Idempotency-Key") String idemKey,
            @AuthenticationPrincipal Long customerId,
            @Valid @RequestBody ApproveRequest body
    ) {

        if (idemKey == null || idemKey.isBlank()) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        IdempotentResult<PaymentIntentDetailResponse> res =
                paymentIntentService.approve(intentId, idemKey, customerId, body);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(res.getHttpStatus());
        if (res.getRetryAfterSeconds() != null) {
            builder.header("Retry-After", String.valueOf(res.getRetryAfterSeconds()));
        }

        String msg = res.isReplay()
                ? "이전에 처리된 요청의 결과입니다."
                : (res.getHttpStatus().is2xxSuccessful()
                    ? "결제가 승인되었습니다."
                    : "요청 처리에 실패했습니다.");

        return builder.body(ApiResponse.success(msg, res.getHttpStatus().value(), res.getBody()));
    }
}