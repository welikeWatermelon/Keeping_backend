package com.ssafy.keeping.domain.payment.intent.controller;

import com.ssafy.keeping.domain.idempotency.model.IdempotentResult;
import com.ssafy.keeping.domain.payment.intent.dto.PaymentInitiateRequest;
import com.ssafy.keeping.domain.payment.intent.dto.PaymentIntentDetailResponse;
import com.ssafy.keeping.domain.payment.intent.service.PaymentIntentService;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PaymentIntentController {

    private final PaymentIntentService paymentIntentService;

    /**
     * 점원/점주가 손님 QR을 스캔하고 결제 의도를 생성
     * 헤더: Authorization(점원 인증), Idempotency-Key(멱등)
     * 경로: qrTokenId = QR 토큰 UUID
     */
    @PostMapping("/cpqr/{qrTokenId}/initiate")
    public ResponseEntity<ApiResponse<PaymentIntentDetailResponse>> initiate(
            @PathVariable UUID qrTokenId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            // @AuthenticationPrincipal Owner owner,
            @AuthenticationPrincipal Long ownerId,
            @Valid @RequestBody PaymentInitiateRequest body
    ) {
        IdempotentResult<PaymentIntentDetailResponse> res = paymentIntentService.initiate(qrTokenId, idempotencyKeyHeader, ownerId, body);
        return ResponseEntity
                .status(res.getHttpStatus())
                .body(ApiResponse.success(
                        res.isReplay() ? "이전에 처리된 요청의 결과입니다." : "결제 요청이 생성되었습니다.",
                        res.getHttpStatus().value(),
                        res.getBody()));
    }

    /**
     * 의도 상세 조회(손님/점주 공용)
     * Path: 외부 공개 UUID
     */
    @GetMapping("/api/payments/intent/{intentPublicId}")
    public ResponseEntity<ApiResponse<PaymentIntentDetailResponse>> getDetail(
            @PathVariable UUID intentPublicId
    ) {
        PaymentIntentDetailResponse res = paymentIntentService.getDetail(intentPublicId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("OK", HttpStatus.OK.value(), res));
    }
    // 202 Accepted(처리중) 이후 재조회 - 멱등키 선점으로 202 Accepted + Retry-After가 온 경우 → 지정 시간 뒤 이 상세 API로 최종 결과 재조회.
    // 푸시로 진입  - “결제 요청이 도착했어요” 푸시를 눌러 앱/웹이 열릴 때 payload에 intentPublicId만 담고, 화면 진입 시 이 API로 전체 상세(메뉴 스냅샷, 금액, 상태) 로드.
}