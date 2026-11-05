package com.smartshift.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** A separate scrape identity; it cannot log into the app or access other Actuator endpoints. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true")
public class MonitoringSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain prometheusSecurity(HttpSecurity http, PasswordEncoder encoder,
            @Value("${app.monitoring.password-file}") Path passwordFile) throws Exception {
        String password = readPassword(passwordFile);
        var identity = User.withUsername("prometheus").password(encoder.encode(password)).roles("MONITORING").build();
        var provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(identity));
        provider.setPasswordEncoder(encoder);
        return http.securityMatcher("/actuator/prometheus")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                .hasRole("MONITORING").anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults())
            .build();
    }

    static String readPassword(Path file) throws IOException {
        String password = Files.readString(file).strip();
        if (password.length() < 24) throw new IllegalStateException("Metrics password file must contain at least 24 characters");
        return password;
    }
}
