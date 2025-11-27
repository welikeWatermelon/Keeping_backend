package com.ssafy.keeping.domain.otp.adapter;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Value
@ConfigurationProperties(prefix = "sms")
public class SmsProperties {
    String apiKey;
    String apiSecretKey;
    String sendNumber;
    String baseUrl;
}
