package com.ssafy.keeping.domain.otp.adapter;

import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    @Value("${sms.api-key}")   String apiKey;
    @Value("${sms.api-secret}") String apiSecretKey;
    @Value("${sms.base-url}")   String baseUrl;

    @Bean
    public DefaultMessageService messageService(SmsProperties props) {
        return NurigoApp.INSTANCE.initialize(apiKey, apiSecretKey, baseUrl);
    }
}
