package com.ssafy.keeping.global.client;

import lombok.Data;
import lombok.Getter;
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Value
@ConfigurationProperties(prefix = "finopenapi")
public class FinOpenApiProperties {
    String baseUrl;
    String apiKey;
    Timeout timeOutMs;

    @Data
    public static class Timeout {
        private int connect;
        private int read;
    }
}
