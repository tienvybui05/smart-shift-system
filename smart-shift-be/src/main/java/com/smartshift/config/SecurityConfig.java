package com.smartshift.config;

import com.smartshift.security.CustomUserDetailsService;
import com.smartshift.security.ActiveUserJwtValidator;
import com.smartshift.security.RestAccessDeniedHandler;
import com.smartshift.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, BootstrapAdminProperties.class})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
        CustomUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
            userDetailsService
        );
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET phải có ít nhất 32 byte để sử dụng HS256"
            );
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
        SecretKey secretKey,
        JwtProperties properties,
        ActiveUserJwtValidator activeUserJwtValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                activeUserJwtValidator
            )
        );
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter =
            new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
            authoritiesConverter
        );
        return authenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter,
        RestAuthenticationEntryPoint authenticationEntryPoint,
        RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/users/me/password"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/employee-availabilities/users/**"
                ).hasRole("ADMIN")
                .requestMatchers(
                    "/api/employee-availabilities/me/**"
                ).authenticated()
                .requestMatchers(
                    "/api/time-off-requests/me",
                    "/api/time-off-requests/me/**"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/time-off-requests"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/time-off-requests/*/review"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    "/api/time-off-requests/**"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/shift-assignments/me"
                ).authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/open-shift-claims/available",
                    "/api/open-shift-claims/me"
                ).hasRole("EMPLOYEE")
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/open-shift-claims"
                ).hasRole("EMPLOYEE")
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/open-shift-claims/me/*/cancel"
                ).hasRole("EMPLOYEE")
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/open-shift-claims"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/open-shift-claims/*/review"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/positions/**"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    "/api/schedules/**",
                    "/api/schedule-periods/**",
                    "/api/shift-assignments/**",
                    "/api/shift-requirements/**",
                    "/api/shift-templates/**",
                    "/api/work-shifts/**"
                ).hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(
                    "/api/locations/**",
                    "/api/positions/**",
                    "/api/roles/**",
                    "/api/users/**"
                ).hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                )
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }
}
