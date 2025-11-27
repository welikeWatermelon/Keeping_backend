package com.ssafy.keeping.domain.otp.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.Gender;
import com.ssafy.keeping.domain.auth.enums.UserRole;
import com.ssafy.keeping.domain.otp.dto.OtpRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegSession {
    private UserRole userRole;
    private String regSessionId;
    private String providerId;
    private AuthProvider provider;
    private String email;
    private String imgUrl;
    private String name;
    private String phoneNumber;
    private LocalDate birth;
    private Gender gender;
    private LocalDateTime phoneVerifiedAt;
    private RegStep regStep;

    public static RegSession fromOtpRequest(OtpRequest dto, String regSessionId) {
        Gender gender = Gender.genderFromDigit(dto.getGenderDigit());
        return RegSession.builder()
                .regSessionId(regSessionId)
                .name(dto.getName())
                .phoneNumber(dto.getPhoneNumber())
                .birth(dto.getBirth())
                .gender(gender)
                .regStep(RegStep.OTP_SENT)
                .build();
    }

    public void markVerifiedAt() {
        this.regStep = RegStep.PHONE_VERIFIED;
    }


}
