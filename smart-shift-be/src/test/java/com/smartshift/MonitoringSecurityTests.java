package com.smartshift;

import com.smartshift.security.ActiveUserJwtValidator;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MonitoringSecurityTests {
    static final String PASSWORD = "isolated-monitoring-test-password-2026";
    static final Path SECRET;
    static {
        try { SECRET = Files.createTempFile("monitoring-test-", ".secret"); Files.writeString(SECRET, PASSWORD); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("app.monitoring.enabled", () -> "true");
        registry.add("app.monitoring.password-file", SECRET::toString);
    }
    @AfterAll static void cleanup() throws Exception { Files.deleteIfExists(SECRET); }
    @LocalServerPort int port;
    @Autowired JwtEncoder encoder;
    @Autowired ApplicationContext context;
    @MockitoBean ActiveUserJwtValidator activeUsers;
    final HttpClient client = HttpClient.newHttpClient();
    @BeforeEach void validTokens() { when(activeUsers.validate(any())).thenReturn(OAuth2TokenValidatorResult.success()); }
    HttpResponse<String> get(String path, String auth) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (auth != null) request.header("Authorization", auth);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    String basic(String password) { return "Basic " + Base64.getEncoder().encodeToString(("prometheus:" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    String jwt(String role) {
        var claims = JwtClaimsSet.builder().issuer("smart-shift-system").subject("monitoring-test")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120)).claim("roles", List.of(role)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @Test void anonymousHealthHidesComponents() throws Exception {
        var response = get("/actuator/health", null);
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains("UP"));
        assertFalse(response.body().contains("components")); assertFalse(response.body().contains("details"));
    }
    @Test void publicProbesAreReachable() throws Exception {
        assertEquals(200, get("/livez", null).statusCode()); assertEquals(200, get("/readyz", null).statusCode());
    }
    @Test void refusingTrafficChangesReadinessWithoutChangingLiveness() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            assertEquals(503, get("/readyz", null).statusCode());
            assertEquals(200, get("/livez", null).statusCode());
        } finally { AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC); }
    }
    @Test void anonymousCannotReadMetrics() throws Exception { assertEquals(401, get("/actuator/metrics", null).statusCode()); }
    @Test void managerCannotReadMetrics() throws Exception { assertEquals(403, get("/actuator/metrics", jwt("ROLE_MANAGER")).statusCode()); }
    @Test void adminCanReadMetricsAndHealthDetails() throws Exception {
        assertEquals(200, get("/actuator/metrics", jwt("ROLE_ADMIN")).statusCode());
        assertTrue(get("/actuator/health", jwt("ROLE_ADMIN")).body().contains("components"));
    }
    @Test void scrapeRequiresCorrectPassword() throws Exception {
        assertEquals(401, get("/actuator/prometheus", null).statusCode());
        assertEquals(401, get("/actuator/prometheus", basic("incorrect")).statusCode());
        var response = get("/actuator/prometheus", basic(PASSWORD));
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains("jvm_memory_used_bytes"));
    }
    @Test void scrapeIdentityCannotAccessApplicationOrOtherActuatorEndpoints() throws Exception {
        assertEquals(401, get("/api/users", basic(PASSWORD)).statusCode());
        assertEquals(401, get("/actuator/metrics", basic(PASSWORD)).statusCode());
    }
}
