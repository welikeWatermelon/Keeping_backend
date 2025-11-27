package com.ssafy.keeping.global.exception.handlers;


import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import com.ssafy.keeping.global.exception.dto.ExceptionDto;
import com.ssafy.keeping.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. @RequestBody 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 유효하지 않습니다.");

        log.warn("Validation error: {} at {}", errorMessage, request.getRequestURI());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(errorMessage, HttpStatus.BAD_REQUEST.value()));
    }

    // 2. @RequestParam, @PathVariable 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String errorMessage = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");

        log.warn("Constraint violation: {} at {}", errorMessage, request.getRequestURI());

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(errorMessage, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("DataIntegrityViolationException 발생: {}", e.getMessage(), e);

        if (isUniqueViolation(e)) {
            // 정확한 에러 메시지 로깅
            Throwable cause = e.getCause();
            while (cause != null) {
                log.error("Cause: {}", cause.getMessage());
                cause = cause.getCause();
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("중복되는 입력값입니다.", 409));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("무결성 제약 위반", 400));
    }

    private boolean isUniqueViolation(Throwable t) {
        while (t != null) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve) {
                // 자세한 정보 로깅 추가
                log.error("ConstraintViolationException - SQL: {}, SQLState: {}, ErrorCode: {}",
                        cve.getSQL(), cve.getSQLState(), cve.getErrorCode());

                String state = cve.getSQLState();
                int code = cve.getErrorCode();
                if ("23000".equals(state) || "23505".equals(state) || code == 1062) return true;
            }
            if (t instanceof java.sql.SQLIntegrityConstraintViolationException) {
                log.error("SQLIntegrityConstraintViolationException: {}", t.getMessage());
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    // 3. 커스텀 예외
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("Custom exception: {}", errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getMessage(), errorCode.getHttpStatus().value()));
    }

    // 4. 알 수 없는 예외 (최종 fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}