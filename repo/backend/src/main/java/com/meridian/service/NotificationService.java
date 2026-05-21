package com.meridian.service;

import com.meridian.entity.Notification;
import com.meridian.entity.NotificationTemplate;
import com.meridian.exception.AppException;
import com.meridian.repository.NotificationRepository;
import com.meridian.repository.NotificationTemplateRepository;
import com.meridian.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final SseNotificationService sseNotificationService;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationTemplateRepository notificationTemplateRepository,
                                SseNotificationService sseNotificationService,
                                UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.sseNotificationService = sseNotificationService;
        this.userRepository = userRepository;
    }

    public Notification sendNotification(UUID userId, String type, String subject, String body) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(normalizeType(type));
        notification.setSubject(subject);
        notification.setBody(body);

        Notification saved = notificationRepository.save(notification);

        String payload = "{\"subject\":\"" + escapeJson(subject) + "\",\"body\":\"" + escapeJson(body) + "\"}";
        sseNotificationService.push(userId, type, payload);

        log.info("Notification sent to user {} with type {}", userId, type);
        return saved;
    }

    public Notification sendFromTemplate(UUID userId, String templateName, Map<String, String> vars) {
        NotificationTemplate template = notificationTemplateRepository.findByName(templateName)
                .orElseThrow(() -> AppException.notFound("Notification template not found: " + templateName));

        String subject = replacePlaceholders(template.getSubject(), vars);
        String body = replacePlaceholders(template.getBody(), vars);

        return sendNotification(userId, "SYSTEM", subject, body);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    public Notification markRead(UUID notifId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notifId, userId)
                .orElseThrow(() -> AppException.forbidden("Notification not found or access denied: " + notifId));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.debug("Marked notification {} as read for user {}", notifId, userId);
        }

        return notification;
    }

    public void notify(UUID userId, String type, String message) {
        sendNotification(userId, type, type, message);
    }

    public void notifyRole(String roleName, String type, String message) {
        log.info("Broadcast notification to role={} type={}: {}", roleName, type, message);
        userRepository.findAll().stream()
                .filter(u -> u.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals(roleName)))
                .forEach(u -> sendNotification(u.getId(), type, type, message));
    }

    private String replacePlaceholders(String template, Map<String, String> vars) {
        if (vars == null || template == null) return template;
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String normalizeType(String type) {
        if (type == null) return "SYSTEM";
        return switch (type.toUpperCase()) {
            case "APPROVAL_REQUEST", "APPROVAL_REQUESTED" -> "APPROVAL_REQUEST";
            case "ACCOUNT_STATUS" -> "ACCOUNT_STATUS";
            case "EXPORT_COMPLETE" -> "EXPORT_COMPLETE";
            case "ANOMALY_ALERT" -> "ANOMALY_ALERT";
            default -> "SYSTEM";
        };
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
