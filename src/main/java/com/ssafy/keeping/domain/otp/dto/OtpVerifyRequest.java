package com.ssafy.keeping.domain.otp.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyRequest {

    private String regSessionId;

    @Pattern(regexp = "^[0-9]{6}$", message = "OTP 인증번호는 6자리 입니다.")
    private String code;
}
