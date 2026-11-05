package com.smartshift;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.monitoring.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiDocumentationTests {
    @LocalServerPort int port;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    final HttpClient client = HttpClient.newHttpClient();
    JsonNode spec;
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @BeforeAll void load() throws Exception {
        var response = get("/v3/api-docs");
        assertEquals(200, response.statusCode(), response.body());
        spec = Json.mapper().readTree(response.body());
    }
    JsonNode operation(String path, String method) { return spec.path("paths").path(path).path(method); }
    @Test void allControllerRoutesHaveDocumentation() {
        Set<String> actual = new TreeSet<>(), documented = new TreeSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().equals("com.smartshift.controller")) return;
            for (String path : mapping.getPatternValues()) for (var method : mapping.getMethodsCondition().getMethods())
                actual.add(method.name().toLowerCase(Locale.ROOT) + " " + path);
        });
        spec.path("paths").fields().forEachRemaining(path -> {
            if (!path.getKey().startsWith("/api/")) return;
            path.getValue().fields().forEachRemaining(method -> {
                if (Set.of("get","post","put","patch","delete").contains(method.getKey())) {
                    documented.add(method.getKey() + " " + path.getKey());
                    assertFalse(method.getValue().path("summary").asText().isBlank());
                    assertFalse(method.getValue().path("tags").isEmpty());
                }
            });
        });
        assertFalse(actual.isEmpty()); assertEquals(actual, documented);
    }
    @Test void loginIsPublicAndOtherBusinessApisRequireBearer() {
        assertTrue(operation("/api/auth/login", "post").path("security").isEmpty());
        assertTrue(operation("/api/auth/me", "get").path("security").get(0).has("bearerAuth"));
        assertEquals("/", spec.path("servers").get(0).path("url").asText());
        assertEquals("bearer", spec.at("/components/securitySchemes/bearerAuth/scheme").asText());
    }
    @Test void responseStatusAndStreamingBinaryContractsMatchControllers() {
        assertTrue(operation("/api/auth/me", "get").path("responses").path("200").path("content").has("application/json"));
        assertTrue(operation("/api/users", "post").path("responses").has("201"));
        assertFalse(operation("/api/users", "post").path("responses").has("200"));
        assertTrue(operation("/api/users/me/password", "patch").path("responses").has("204"));
        assertEquals("binary", operation("/api/attendances/export", "get").path("responses").path("200").path("content")
            .path("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").path("schema").path("format").asText());
        assertTrue(operation("/api/notifications/stream", "get").path("responses").path("200").path("content").has("text/event-stream"));
    }
    @Test void validationPaginationAndPasswordsAreDescribed() {
        JsonNode login = spec.at("/components/schemas/LoginRequest");
        assertTrue(login.path("required").toString().contains("username"));
        assertTrue(login.at("/properties/password/writeOnly").asBoolean());
        assertEquals("password", login.at("/properties/password/format").asText());
        JsonNode size = null;
        for (JsonNode param : operation("/api/users", "get").path("parameters")) if (param.path("name").asText().equals("size")) size = param;
        assertNotNull(size); assertEquals(1, size.path("schema").path("minimum").asInt());
        assertEquals(100, size.path("schema").path("maximum").asInt());
    }
    @Test void allReferencesResolveAndOperationIdsAreUnique() {
        assertReferences(spec);
        Set<String> ids = new HashSet<>();
        spec.path("paths").elements().forEachRemaining(path -> path.elements().forEachRemaining(op -> {
            if (op.has("operationId")) assertTrue(ids.add(op.path("operationId").asText()), "duplicate operationId");
        }));
    }
    void assertReferences(JsonNode node) {
        if (node.has("$ref")) {
            String ref = node.path("$ref").asText();
            assertTrue(ref.startsWith("#/"), ref);
            assertFalse(spec.at(ref.substring(1)).isMissingNode(), "Unresolved " + ref);
        }
        if (node.isContainerNode()) node.elements().forEachRemaining(this::assertReferences);
    }
    @Test void docsAreAccessibleButBusinessAuthenticationIsPreserved() throws Exception {
        assertEquals(200, get("/v3/api-docs.yaml").statusCode());
        assertEquals(200, get("/swagger-ui/index.html").statusCode());
        assertEquals(200, get("/v3/api-docs/swagger-config").statusCode());
        assertEquals(401, get("/api/auth/me").statusCode());
        assertEquals(401, get("/api/users").statusCode());
    }
}
