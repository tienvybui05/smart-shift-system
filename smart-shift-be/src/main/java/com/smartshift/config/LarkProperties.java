package com.smartshift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.integrations.lark")
public record LarkProperties(
    boolean enabled,
    String webhookUrl,
    String secret,
    boolean appEnabled,
    String appId,
    String appSecret,
    String apiBaseUrl,
    String smartShiftUrl,
    Duration connectTimeout,
    Duration requestTimeout,
    int maxAttempts,
    Duration retryDelay
) {

    public boolean configured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    public boolean signatureEnabled() {
        return secret != null && !secret.isBlank();
    }

    public boolean groupReady() {
        return enabled && configured();
    }

    public boolean appConfigured() {
        return appId != null && !appId.isBlank()
            && appSecret != null && !appSecret.isBlank();
    }

    public boolean personalReady() {
        return appEnabled && appConfigured();
    }
}
