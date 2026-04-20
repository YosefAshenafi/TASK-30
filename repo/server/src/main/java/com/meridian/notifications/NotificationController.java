package com.meridian.notifications;

import com.meridian.common.web.PageResponse;
import com.meridian.notifications.dto.NotificationDto;
import com.meridian.notifications.entity.InAppNotification;
import com.meridian.notifications.entity.NotificationTemplate;
import com.meridian.notifications.repository.InAppNotificationRepository;
import com.meridian.notifications.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final InAppNotificationRepository notifRepo;
    private final NotificationTemplateRepository templateRepo;
    private final TemplateRenderer renderer;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDto>> list(
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        size = Math.min(size, 200);

        List<InAppNotification> all = unread
                ? notifRepo.findUnreadByUserId(userId, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : notifRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        long total = unread ? notifRepo.countUnreadByUserId(userId) : notifRepo.countByUserId(userId);

        List<NotificationDto> items = all.stream().map(n -> toDto(n)).toList();
        return ResponseEntity.ok(new PageResponse<>(items, page, size, total));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth) {
        InAppNotification n = notifRepo.findById(id)
                .filter(notif -> notif.getUserId().equals(UUID.fromString(auth.getName())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setReadAt(Instant.now());
        notifRepo.save(n);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<InAppNotification> unread = notifRepo.findUnreadByUserId(userId,
                PageRequest.of(0, 1000, Sort.by("createdAt").descending()));
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        notifRepo.saveAll(unread);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        long count = notifRepo.countUnreadByUserId(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    private NotificationDto toDto(InAppNotification n) {
        NotificationTemplate tmpl = templateRepo.findById(n.getTemplateKey()).orElse(null);
        String subject = tmpl != null ? renderer.render(tmpl.getTitleTmpl(), Map.of()) : n.getTemplateKey();
        String bodyHtml = tmpl != null ? renderer.renderToHtml(tmpl.getBodyTmpl(), Map.of()) : "";
        String severity = n.getSeverity() != null ? n.getSeverity() : "INFO";
        return new NotificationDto(n.getId(), n.getUserId(), n.getTemplateKey(),
                n.getPayload(),
                new NotificationDto.RenderedContent(subject, bodyHtml),
                severity, n.getReadAt(), n.getCreatedAt());
    }
}
