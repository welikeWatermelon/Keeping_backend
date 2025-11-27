package com.ssafy.keeping.domain.auth.dto;

import com.ssafy.keeping.domain.user.customer.model.Customer;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private Long id;
    private String userType; // "customer"
    private String email;
    private String name;

    // Customer 엔티티에서 CustomerResponse로 변환하는 정적 메서드
    public static CustomerResponse from(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getCustomerId()) // customerId 사용
                .userType("customer")
                .email(customer.getEmail())
                .name(customer.getName())
                .build();
    }
}