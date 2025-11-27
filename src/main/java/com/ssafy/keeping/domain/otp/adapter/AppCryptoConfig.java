package com.ssafy.keeping.domain.otp.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class AppCryptoConfig {
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
