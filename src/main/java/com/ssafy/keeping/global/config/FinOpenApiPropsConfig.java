package com.ssafy.keeping.global.config;

import com.ssafy.keeping.global.client.FinOpenApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FinOpenApiProperties.class)
public class FinOpenApiPropsConfig {
}
