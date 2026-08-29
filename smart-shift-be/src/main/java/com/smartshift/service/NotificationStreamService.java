package com.smartshift.service;

import com.smartshift.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class NotificationStreamService {

    private static final long SSE_TIMEOUT = 0L;
    private static final long CLIENT_RETRY_MILLIS = 3_000L;

    private final Clock clock;
    private final Map<String, Set<SseEmitter>> emittersByUsername =
        new ConcurrentHashMap<>();

    public SseEmitter subscribe(String username) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        Set<SseEmitter> userEmitters = emittersByUsername.computeIfAbsent(
            username,
            ignored -> ConcurrentHashMap.newKeySet()
        );
        userEmitters.add(emitter);

        Runnable removeEmitter = () -> remove(username, emitter);
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(ignored -> removeEmitter.run());

        try {
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .reconnectTime(CLIENT_RETRY_MILLIS)
                    .data(Map.of("connectedAt", clock.instant()))
            );
        } catch (IOException | IllegalStateException exception) {
            remove(username, emitter);
            emitter.complete();
        }
        return emitter;
    }

    public void send(
        String username,
        NotificationResponse notification
    ) {
        Set<SseEmitter> userEmitters = emittersByUsername.get(username);
        if (userEmitters == null) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .id(notification.id().toString())
                        .name("notification")
                        .data(notification)
                );
            } catch (IOException | IllegalStateException exception) {
                remove(username, emitter);
            }
        }
    }

    @Scheduled(
        fixedDelayString = "${app.notifications.sse.heartbeat-ms:25000}"
    )
    public void sendHeartbeat() {
        emittersByUsername.forEach((username, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(
                        SseEmitter.event().comment("heartbeat")
                    );
                } catch (IOException | IllegalStateException exception) {
                    remove(username, emitter);
                }
            }
        });
    }

    private void remove(String username, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emittersByUsername.get(username);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emittersByUsername.remove(username, userEmitters);
        }
    }
}
