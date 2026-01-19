package com.ssafy.keeping.user.customer;

import com.ssafy.keeping.domain.auth.enums.AuthProvider;
import com.ssafy.keeping.domain.auth.enums.Gender;
import com.ssafy.keeping.domain.user.customer.dto.CustomerProfileResponse;
import com.ssafy.keeping.domain.user.customer.model.Customer;
import com.ssafy.keeping.domain.user.customer.repository.CustomerRepository;
import com.ssafy.keeping.domain.user.customer.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional // 테스트 끝나면 자동 롤백(기본)
@ActiveProfiles("test")
public class CustomerServiceProfileTest {

    @Autowired CustomerService customerService;
    @Autowired CustomerRepository customerRepository;

    @MockBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @DisplayName("내 프로필 조회 성공")
    void getMyProfile_success() {
        // given: 고객 저장
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

        // when
        CustomerProfileResponse res = customerService.getMyProfile(saved.getCustomerId());

        // then
        assertThat(res.getName()).isEqualTo(saved.getName());
        assertThat(res.getPhoneNumber()).isEqualTo(saved.getPhoneNumber());
        assertThat(res.getEmail()).isEqualTo(saved.getEmail());
        assertThat(res.getImgUrl()).isEqualTo(saved.getImgUrl());
    }
}
