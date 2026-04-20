package com.meridian.users;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.security.audit.AuditEvent;
import com.meridian.security.audit.AuditEventRepository;
import com.meridian.users.dto.UserSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final NotificationService notificationService;

    public List<UserSummaryDto> listUsers(String status) {
        return userRepository.findByStatusFilter(status).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void approve(UUID targetId, UUID actorId) {
        User user = findActive(targetId);
        if (!"PENDING".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is not in PENDING status");
        }
        user.setStatus("ACTIVE");
        userRepository.save(user);

        auditEventRepository.save(AuditEvent.of(
                actorId, "PERMISSION_CHANGE", "USER", targetId.toString(),
                "{\"action\":\"APPROVE\",\"newStatus\":\"ACTIVE\"}"));

        notificationService.send(targetId, "approval.decided",
                "{\"type\":\"ROLE_APPROVAL\",\"decision\":\"APPROVED\"}");
    }

    @Transactional
    public void reject(UUID targetId, UUID actorId, String reason) {
        User user = findActive(targetId);
        if (!"PENDING".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is not in PENDING status");
        }
        user.setStatus("SUSPENDED");
        userRepository.save(user);

        auditEventRepository.save(AuditEvent.of(
                actorId, "PERMISSION_CHANGE", "USER", targetId.toString(),
                "{\"action\":\"REJECT\",\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}"));

        notificationService.send(targetId, "approval.decided",
                "{\"type\":\"ROLE_APPROVAL\",\"decision\":\"REJECTED\"}");
    }

    @Transactional
    public void unlock(UUID targetId, UUID actorId) {
        User user = findActive(targetId);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        if ("LOCKED".equals(user.getStatus())) {
            user.setStatus("ACTIVE");
        }
        userRepository.save(user);

        auditEventRepository.save(AuditEvent.of(
                actorId, "UNLOCK", "USER", targetId.toString(), "{}"));
    }

    public UserSummaryDto getById(UUID id) {
        return toSummary(findActive(id));
    }

    @Transactional
    public void updateStatus(UUID targetId, String status, UUID actorId) {
        User user = findActive(targetId);
        String old = user.getStatus();
        user.setStatus(status);
        userRepository.save(user);
        auditEventRepository.save(AuditEvent.of(
                actorId, "PERMISSION_CHANGE", "USER", targetId.toString(),
                "{\"action\":\"STATUS_UPDATE\",\"from\":\"" + old + "\",\"to\":\"" + status + "\"}"));
    }

    private User findActive(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserSummaryDto toSummary(User u) {
        return new UserSummaryDto(u.getId(), u.getUsername(), u.getDisplayName(),
                u.getRole(), u.getStatus(), u.getOrganizationId(), u.getCreatedAt());
    }
}
