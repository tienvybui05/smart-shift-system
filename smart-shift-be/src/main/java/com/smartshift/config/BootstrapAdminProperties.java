package com.smartshift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.bootstrap-admin")
public record BootstrapAdminProperties(
    boolean enabled,
    String username,
    String password
) {
}
