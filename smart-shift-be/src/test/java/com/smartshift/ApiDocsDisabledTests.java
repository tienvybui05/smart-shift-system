package com.smartshift;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class ApiDocsDisabledTests {
    @LocalServerPort int port;
    int status(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    @Test void generatedDocumentsAreUnavailableWhenDisabled() throws Exception {
        assertEquals(404,status("/v3/api-docs")); assertEquals(404,status("/v3/api-docs.yaml"));
    }
    @Test void swaggerAssetsAreUnavailableWhenDisabled() throws Exception { assertEquals(404,status("/swagger-ui/index.html")); }
    @Test void disablingDocsPreservesHealthAndBusinessSecurity() throws Exception {
        assertEquals(200,status("/livez")); assertEquals(401,status("/api/users"));
    }
}
