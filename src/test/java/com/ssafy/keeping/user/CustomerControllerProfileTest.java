package com.ssafy.keeping.user;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.Gender;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.support.MySqlTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class CustomerControllerProfileTest extends MySqlTestContainerConfig {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;

    @Test
    @DisplayName("내 프로필 조회 성공 - /customers/me")
    void getMyProfile_success() throws Exception {
        // given
        Customer saved = customerRepository.save(Customer.builder()
                .providerType(AuthProvider.KAKAO)
                .providerId("kakao-" + UUID.randomUUID())
                .name("홍길동")
                .email("hong" + UUID.randomUUID() + "@test.com")
                .gender(Gender.MALE)
                .birth(LocalDate.of(1998, 1, 1))
                .imgUrl("https://test/img.png")
                .phoneNumber("010" + (int)(Math.random() * 1_0000_0000))
                .build());

        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        saved.getCustomerId(), // principal = customerId
                        null, // credentials
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")) // authorities 권한 목록
                );

        // when & then
        mockMvc.perform(get("/customers/me")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 조회 성공"))
                .andExpect(jsonPath("$.data.name").value(saved.getName()))
                .andExpect(jsonPath("$.data.phoneNumber").value(saved.getPhoneNumber()))
                .andExpect(jsonPath("$.data.email").value(saved.getEmail()))
                .andExpect(jsonPath("$.data.imgUrl").value(saved.getImgUrl()));

    }



}
