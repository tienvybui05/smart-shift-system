package com.smartshift.integration.lark;

import com.smartshift.config.LarkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LarkAppClient {

    private static final int MAX_STORED_RESPONSE_LENGTH = 2000;

    private final HttpClient larkHttpClient;
    private final LarkProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile CachedToken cachedToken;

    public LarkUserLookupResult findUser(String email, String phoneNumber) {
        if (!properties.personalReady()) {
            return lookupFailed("Lark App Bot chưa được cấu hình đầy đủ");
        }
        if (isBlank(email) && isBlank(phoneNumber)) {
            return lookupFailed(
                "Nhân viên chưa có email hoặc số điện thoại để đối chiếu Lark"
            );
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (!isBlank(email)) {
                payload.put("emails", List.of(email.trim()));
            }
            if (!isBlank(phoneNumber)) {
                payload.put("mobiles", List.of(phoneNumber.trim()));
            }
            payload.put("include_resigned", false);

            HttpResponse<String> response = sendAuthorized(
                "/contact/v3/users/batch_get_id?user_id_type=open_id",
                payload
            );
            if (!isHttpSuccess(response.statusCode())) {
                return lookupFailed(
                    "Lark trả về HTTP " + response.statusCode()
                );
            }
            Map<?, ?> body = readMap(response.body());
            if (!isSuccessCode(body.get("code"))) {
                return lookupFailed(apiError(body));
            }
            Object dataValue = body.get("data");
            if (!(dataValue instanceof Map<?, ?> data)) {
                return lookupFailed("Phản hồi tra cứu người dùng Lark bị thiếu dữ liệu");
            }
            Object usersValue = data.get("user_list");
            if (!(usersValue instanceof List<?> users) || users.isEmpty()) {
                return lookupFailed(
                    "Không tìm thấy tài khoản Lark khớp email hoặc số điện thoại"
                );
            }
            for (Object value : users) {
                if (value instanceof Map<?, ?> user) {
                    Object openId = user.get("user_id");
                    if (openId != null && !openId.toString().isBlank()) {
                        return new LarkUserLookupResult(
                            true,
                            openId.toString(),
                            null
                        );
                    }
                }
            }
            return lookupFailed("Lark không trả về open_id của người dùng");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return lookupFailed("Tiến trình đồng bộ Lark đã bị gián đoạn");
        } catch (Exception exception) {
            return lookupFailed(
                "Không thể tra cứu tài khoản Lark: " + safeMessage(exception)
            );
        }
    }

    public LarkClientResult sendMessage(
        String openId,
        String title,
        String content
    ) {
        if (!properties.personalReady()) {
            return failed(null, null, "Lark App Bot chưa được cấu hình đầy đủ");
        }
        if (isBlank(openId)) {
            return failed(null, null, "Nhân viên chưa liên kết tài khoản Lark");
        }

        try {
            String messageContent = objectMapper.writeValueAsString(
                Map.of("text", "[Smart Shift] " + title + "\n" + content)
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("receive_id", openId);
            payload.put("msg_type", "text");
            payload.put("content", messageContent);
            HttpResponse<String> response = sendAuthorized(
                "/im/v1/messages?receive_id_type=open_id",
                payload
            );
            String responseBody = truncate(response.body());
            if (!isHttpSuccess(response.statusCode())) {
                return failed(
                    response.statusCode(),
                    responseBody,
                    "Lark trả về HTTP " + response.statusCode()
                );
            }
            Map<?, ?> body = readMap(response.body());
            if (isSuccessCode(body.get("code"))) {
                return new LarkClientResult(
                    true,
                    response.statusCode(),
                    responseBody,
                    null
                );
            }
            return failed(
                response.statusCode(),
                responseBody,
                apiError(body)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(null, null, "Tiến trình gửi Lark đã bị gián đoạn");
        } catch (Exception exception) {
            return failed(
                null,
                null,
                "Không thể gửi tin nhắn Lark: " + safeMessage(exception)
            );
        }
    }

    private HttpResponse<String> sendAuthorized(
        String path,
        Map<String, Object> payload
    ) throws Exception {
        String token = getTenantAccessToken();
        HttpResponse<String> response = send(path, payload, token);
        if (response.statusCode() == 401) {
            clearCachedToken();
            response = send(path, payload, getTenantAccessToken());
        }
        return response;
    }

    private HttpResponse<String> send(
        String path,
        Map<String, Object> payload,
        String accessToken
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(apiUri(path))
            .timeout(properties.requestTimeout())
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(payload)
            ))
            .build();
        return larkHttpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private synchronized String getTenantAccessToken() throws Exception {
        Instant now = clock.instant();
        if (cachedToken != null
            && cachedToken.expiresAt().isAfter(now.plusSeconds(60))) {
            return cachedToken.value();
        }

        Map<String, Object> payload = Map.of(
            "app_id", properties.appId().trim(),
            "app_secret", properties.appSecret().trim()
        );
        HttpRequest request = HttpRequest.newBuilder()
            .uri(apiUri("/auth/v3/tenant_access_token/internal"))
            .timeout(properties.requestTimeout())
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(payload)
            ))
            .build();
        HttpResponse<String> response = larkHttpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (!isHttpSuccess(response.statusCode())) {
            throw new IllegalStateException(
                "Không thể lấy access token Lark, HTTP " + response.statusCode()
            );
        }
        Map<?, ?> body = readMap(response.body());
        if (!isSuccessCode(body.get("code"))) {
            throw new IllegalStateException(apiError(body));
        }
        Object token = body.get("tenant_access_token");
        if (token == null || token.toString().isBlank()) {
            throw new IllegalStateException("Lark không trả về tenant access token");
        }
        long expiresIn = numberValue(body.get("expire"), 7200L);
        cachedToken = new CachedToken(
            token.toString(),
            now.plusSeconds(Math.max(120L, expiresIn))
        );
        return cachedToken.value();
    }

    private synchronized void clearCachedToken() {
        cachedToken = null;
    }

    private URI apiUri(String path) {
        String baseUrl = properties.apiBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + path);
    }

    private Map<?, ?> readMap(String value) {
        return objectMapper.readValue(value, Map.class);
    }

    private boolean isSuccessCode(Object value) {
        if (value instanceof Number number) {
            return number.longValue() == 0;
        }
        return value != null && "0".equals(value.toString());
    }

    private boolean isHttpSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private long numberValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private String apiError(Map<?, ?> body) {
        Object message = body.get("msg");
        return "Lark từ chối yêu cầu: "
            + (message == null ? "không rõ nguyên nhân" : message);
    }

    private LarkUserLookupResult lookupFailed(String error) {
        return new LarkUserLookupResult(false, null, truncateError(error));
    }

    private LarkClientResult failed(
        Integer httpStatus,
        String responseBody,
        String error
    ) {
        return new LarkClientResult(
            false,
            httpStatus,
            truncate(responseBody),
            truncateError(error)
        );
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String sanitized = message
            .replace(properties.appSecret(), "[secret]")
            .replace(properties.appId(), "[app-id]");
        return sanitized.length() <= 300
            ? sanitized
            : sanitized.substring(0, 300);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_STORED_RESPONSE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STORED_RESPONSE_LENGTH);
    }

    private String truncateError(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
