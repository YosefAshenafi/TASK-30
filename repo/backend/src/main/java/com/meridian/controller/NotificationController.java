package com.meridian.controller;

import com.meridian.entity.Notification;
import com.meridian.entity.User;
import com.meridian.service.NotificationService;
import com.meridian.service.SseNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;
    private final SseNotificationService sseNotificationService;

    public NotificationController(NotificationService notificationService,
                                   SseNotificationService sseNotificationService) {
        this.notificationService = notificationService;
        this.sseNotificationService = sseNotificationService;
    }

    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @AuthenticationPrincipal User principal,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Notification> notifications = notificationService.getNotifications(
                principal.getId(), pageable);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Notification> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal User principal) {

        Notification notification = notificationService.markRead(id, principal.getId());
        return ResponseEntity.ok(notification);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(
            @AuthenticationPrincipal User principal) {

        return sseNotificationService.register(principal.getId());
    }
}
