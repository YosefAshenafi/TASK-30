package com.meridian.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> userEmitters =
            new ConcurrentHashMap<>();

    public SseEmitter register(UUID userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> removeEmitter(userId, emitter));

        // Send an initial comment so the response headers (200 + text/event-stream) are flushed
        // to the client and any reverse proxy immediately on connect, rather than blocking until
        // the first real notification arrives.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        log.debug("Registered SSE emitter for user {}", userId);
        return emitter;
    }

    public void push(UUID userId, String type, String payload) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(type)
                        .data(payload));
            } catch (IOException e) {
                log.debug("SSE emitter for user {} is dead, scheduling removal", userId);
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
        log.debug("Removed SSE emitter for user {}", userId);
    }
}
