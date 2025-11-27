package com.ssafy.keeping.domain.user.owner.dto;

import com.ssafy.keeping.domain.auth.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerRegisterRequest {

    @NotBlank
    private String regSessionId;
}
