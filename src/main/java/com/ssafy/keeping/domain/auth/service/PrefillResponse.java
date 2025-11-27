package com.ssafy.keeping.domain.auth.service;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class PrefillResponse {

    @NotBlank
    private String name;

    @NotBlank
    private LocalDate birth;

    @NotBlank
    private String phoneNumber;

}
