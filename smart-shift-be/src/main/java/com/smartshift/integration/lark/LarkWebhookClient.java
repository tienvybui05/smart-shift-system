package com.smartshift.integration.lark;

import com.smartshift.config.LarkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LarkWebhookClient {

    private static final int MAX_STORED_RESPONSE_LENGTH = 2000;

    private final HttpClient larkHttpClient;
    private final LarkProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LarkClientResult send(String title, String content) {
        if (!properties.configured()) {
            return failed(null, null, "Chưa cấu hình LARK_WEBHOOK_URL");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(
                buildPayload(title, content)
            );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.webhookUrl().trim()))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            HttpResponse<String> response = larkHttpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String responseBody = truncate(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failed(
                    response.statusCode(),
                    responseBody,
                    "Lark trả về HTTP " + response.statusCode()
                );
            }
            return parseSuccessResponse(response.statusCode(), responseBody);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(null, null, "Tiến trình gửi Lark đã bị gián đoạn");
        } catch (IllegalArgumentException exception) {
            return failed(null, null, "LARK_WEBHOOK_URL không hợp lệ");
        } catch (Exception exception) {
            return failed(
                null,
                null,
                "Không thể kết nối Lark: " + safeMessage(exception)
            );
        }
    }

    private Map<String, Object> buildPayload(String title, String content)
        throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (properties.signatureEnabled()) {
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            payload.put("timestamp", timestamp);
            payload.put("sign", createSignature(timestamp));
        }
        payload.put("msg_type", "text");
        payload.put(
            "content",
            Map.of("text", "[Smart Shift] " + title + "\n" + content)
        );
        return payload;
    }

    private String createSignature(String timestamp) throws Exception {
        String stringToSign = timestamp + "\n" + properties.secret();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
            stringToSign.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        ));
        return Base64.getEncoder().encodeToString(mac.doFinal(new byte[0]));
    }

    private LarkClientResult parseSuccessResponse(
        int httpStatus,
        String responseBody
    ) {
        try {
            Map<?, ?> body = objectMapper.readValue(responseBody, Map.class);
            Object responseCode = body.containsKey("code")
                ? body.get("code")
                : body.get("StatusCode");
            if (isZero(responseCode)) {
                return new LarkClientResult(
                    true,
                    httpStatus,
                    responseBody,
                    null
                );
            }
            Object message = body.containsKey("msg")
                ? body.get("msg")
                : body.get("StatusMessage");
            return failed(
                httpStatus,
                responseBody,
                "Lark từ chối tin nhắn: "
                    + (message == null ? "không rõ nguyên nhân" : message)
            );
        } catch (Exception exception) {
            return failed(
                httpStatus,
                responseBody,
                "Phản hồi từ Lark không đúng định dạng"
            );
        }
    }

    private boolean isZero(Object value) {
        if (value instanceof Number number) {
            return number.longValue() == 0;
        }
        return value != null && "0".equals(value.toString());
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
        return message.length() <= 300 ? message : message.substring(0, 300);
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
}
