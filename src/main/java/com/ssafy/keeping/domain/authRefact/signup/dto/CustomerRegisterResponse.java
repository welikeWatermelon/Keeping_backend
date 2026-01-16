package com.ssafy.keeping.domain.authRefact.signup.dto;

import com.ssafy.keeping.domain.authRefact.enums.AuthProvider;
import com.ssafy.keeping.domain.authRefact.enums.Gender;
import com.ssafy.keeping.domain.user.customer.model.Customer;

import java.time.LocalDate;

public record CustomerRegisterResponse(
        Long customerId,
        AuthProvider providerType,
        String name,
        String email,
        String phoneNumber,
        LocalDate birth,
        Gender gender,
        String imgUrl
) {
    public static CustomerRegisterResponse from(Customer customer) {
        return new CustomerRegisterResponse(
                customer.getCustomerId(),
                customer.getProviderType(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhoneNumber(),
                customer.getBirth(),
                customer.getGender(),
                customer.getImgUrl()
        );
    }
}