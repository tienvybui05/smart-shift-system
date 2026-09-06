package com.smartshift.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(LarkProperties.class)
public class LarkConfig {

    @Bean
    public HttpClient larkHttpClient(LarkProperties properties) {
        return HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
    }
}
