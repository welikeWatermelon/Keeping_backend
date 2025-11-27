package com.ssafy.keeping.domain.user.customer.dto;

import com.ssafy.keeping.domain.auth.service.TokenResponse;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SignupCustomerResponse {
    private CustomerRegisterResponse user;
    private TokenResponse token;
}

