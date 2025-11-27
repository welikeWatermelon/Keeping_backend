package com.ssafy.keeping.domain.user.customer.dto;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.Gender;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegisterResponse {
    private Long customerId;
    private String providerId;
    private AuthProvider providerType;
    private String name;
    private String email;
    private String phoneNumber;
    private LocalDate birth;
    private Gender gender;
    private String imgUrl;

    public static CustomerRegisterResponse register(Customer customer) {
        return CustomerRegisterResponse.builder()
                .customerId(customer.getCustomerId())
                .providerId(customer.getProviderId())
                .providerType(customer.getProviderType())
                .phoneNumber(customer.getPhoneNumber())
                .name(customer.getName())
                .email(customer.getEmail())
                .gender(customer.getGender())
                .birth(customer.getBirth())
                .imgUrl(customer.getImgUrl())
                .build();
    }
}
