package com.meridian.service;

import com.meridian.dto.UserSummaryDto;
import com.meridian.entity.Role;
import com.meridian.entity.User;
import com.meridian.entity.UserStatus;
import com.meridian.exception.AppException;
import com.meridian.repository.DataPermissionRepository;
import com.meridian.repository.RoleRepository;
import com.meridian.repository.UserRepository;
import com.meridian.util.FieldMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final Set<String> UNMASKED_CLASSIFICATIONS = Set.of("PUBLIC", "INTERNAL");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;
    private final DataPermissionRepository dataPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            AuditService auditService,
                            DataPermissionRepository dataPermissionRepository,
                            PasswordEncoder passwordEncoder,
                            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.dataPermissionRepository = dataPermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getPendingUsers() {
        return userRepository.findByStatus(UserStatus.PENDING)
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    public UserSummaryDto approveUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        log.info("User approved: userId={}", id);
        auditService.logEvent(id, "APPROVAL_GRANTED", "USER", id.toString(),
                "{\"action\":\"account_approved\"}", null, null);
        return toSummaryDto(saved);
    }

    public UserSummaryDto rejectUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.REJECTED);
        User saved = userRepository.save(user);
        log.info("User rejected: userId={}", id);
        auditService.logEvent(id, "APPROVAL_REJECTED", "USER", id.toString(),
                "{\"action\":\"account_rejected\"}", null, null);
        return toSummaryDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<UserSummaryDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toSummaryDto);
    }

    public UserSummaryDto changeRole(UUID id, String roleName) {
        User user = findUser(id);
        Role role = resolveRole(roleName);

        String oldRole = user.getPrimaryRole();
        // Replace the role set in place. The loaded User is a managed entity whose `roles` is a
        // Hibernate-managed PersistentSet; assigning an immutable Set.of(...) breaks the merge
        // path (Hibernate calls clear() on the new collection -> UnsupportedOperationException).
        // Mutating the existing collection keeps the managed wrapper intact.
        user.getRoles().clear();
        user.getRoles().add(role);
        User saved = userRepository.save(user);
        log.info("Role changed: userId={}, oldRole={}, newRole={}", id, oldRole, roleName);

        auditService.logEvent(id, "PERMISSION_CHANGE", "USER", id.toString(),
                String.format("{\"oldRole\":\"%s\",\"newRole\":\"%s\"}", oldRole, roleName),
                null, null);

        return toSummaryDto(saved);
    }

    /**
     * Creates a new user account directly (administrator action). The account is created ACTIVE
     * with the supplied role. The role name is accepted with or without the {@code ROLE_} prefix.
     */
    public UserSummaryDto createUser(String username, String rawPassword, String roleName, String organizationId) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw AppException.conflict("Username already exists: " + username);
        }
        Role role = resolveRole(roleName);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        if (organizationId != null && !organizationId.isBlank()) {
            try {
                user.setOrganizationId(UUID.fromString(organizationId.trim()));
            } catch (IllegalArgumentException e) {
                throw AppException.badRequest("Invalid organizationId: " + organizationId);
            }
        }
        user.getRoles().add(role);

        User saved = userRepository.save(user);
        log.info("User created by admin: userId={}, username={}, role={}", saved.getId(), username, role.getName());
        // Audit as a system action (user_id=null). Auditing is @Async and runs on a separate
        // connection; the just-created user row is not yet committed when it fires, so recording
        // the new user as the audit actor would violate audit_events_user_id_fkey and lose the
        // audit record. The new user's id is preserved in the entity_id field instead.
        auditService.logEvent(null, "PERMISSION_CHANGE", "USER", saved.getId().toString(),
                "{\"action\":\"account_created\",\"role\":\"" + role.getName() + "\"}", null, null);
        return toSummaryDto(saved);
    }

    /**
     * Updates a user's account status (e.g. ACTIVE, LOCKED, PENDING, REJECTED).
     */
    public UserSummaryDto updateStatus(UUID id, String statusName) {
        User user = findUser(id);
        UserStatus status;
        try {
            status = UserStatus.valueOf(statusName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Invalid status: " + statusName);
        }
        UserStatus oldStatus = user.getStatus();
        user.setStatus(status);
        User saved = userRepository.save(user);
        log.info("User status changed: userId={}, oldStatus={}, newStatus={}", id, oldStatus, status);
        auditService.logEvent(id, "PERMISSION_CHANGE", "USER", id.toString(),
                String.format("{\"oldStatus\":\"%s\",\"newStatus\":\"%s\"}", oldStatus, status),
                null, null);
        return toSummaryDto(saved);
    }

    /**
     * Permanently (hard) deletes a user and all rows that reference it. Child rows whose foreign
     * keys are not {@code ON DELETE CASCADE} are removed (or nulled, for audit/anomaly history)
     * first, in dependency order, so the final user delete cannot fail with a constraint violation.
     */
    public void deleteUser(UUID id, String requestingUsername) {
        // findUser respects the @SQLRestriction (deleted_at IS NULL); verifies the user exists.
        User user = findUser(id);
        String username = user.getUsername();

        if (username.equals(requestingUsername)) {
            throw AppException.badRequest("You cannot delete your own account");
        }

        // Operational data owned by the user (no ON DELETE CASCADE) — delete in dependency order.
        jdbcTemplate.update("DELETE FROM attempts WHERE user_id = ?", id);
        // training_sessions delete cascades session_activities and draft_assessments.
        jdbcTemplate.update("DELETE FROM training_sessions WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM enrollments WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM certifications WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM report_schedules WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM approvals WHERE requester_id = ?", id);
        jdbcTemplate.update("UPDATE approvals SET approver_id = NULL WHERE approver_id = ?", id);
        jdbcTemplate.update("DELETE FROM recovery_drills WHERE performed_by = ?", id);
        // Preserve audit/anomaly history by detaching the user reference rather than deleting it.
        jdbcTemplate.update("UPDATE audit_events SET user_id = NULL WHERE user_id = ?", id);
        jdbcTemplate.update("UPDATE anomalies SET user_id = NULL WHERE user_id = ?", id);
        jdbcTemplate.update("UPDATE data_permissions SET granted_by = NULL WHERE granted_by = ?", id);
        jdbcTemplate.update("UPDATE recycle_bin SET deleted_by = NULL WHERE deleted_by = ?", id);

        // Final delete cascades user_roles, refresh_tokens, device_fingerprints,
        // and data_permissions(user_id). Native delete bypasses the soft-delete @SQLRestriction.
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);

        log.info("User hard-deleted by admin: userId={}, username={}", id, username);
        auditService.logEvent(null, "DATA_DELETE", "USER", id.toString(),
                "{\"action\":\"account_deleted\",\"username\":\"" + username + "\"}", null, null);
    }

    /**
     * Resolves a role by name, tolerating both the bare ({@code FACULTY_MENTOR}) and
     * prefixed ({@code ROLE_FACULTY_MENTOR}) forms that callers/seed data use.
     */
    private Role resolveRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw AppException.badRequest("Role must not be blank");
        }
        String trimmed = roleName.trim();
        String prefixed = trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
        return roleRepository.findByName(prefixed)
                .or(() -> roleRepository.findByName(trimmed))
                .orElseThrow(() -> AppException.notFound("Role not found: " + roleName));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("User not found: " + id));
    }

    private static String stripRolePrefix(String roleName) {
        if (roleName != null && roleName.startsWith("ROLE_")) {
            return roleName.substring("ROLE_".length());
        }
        return roleName;
    }

    private UserSummaryDto toSummaryDto(User user) {
        Instant deadline = computeBusinessDayDeadline(user.getCreatedAt(), 2);
        boolean overdue = user.getStatus() == UserStatus.PENDING && Instant.now().isAfter(deadline);

        String employeeId = resolveField(user.getId(), "employee_id_enc", user.getEmployeeIdEnc(),
                FieldMaskingUtil::maskEmployeeId);
        String contact = resolveField(user.getId(), "contact_enc", user.getContactEnc(),
                FieldMaskingUtil::maskEmail);

        // Expose the role without the Spring "ROLE_" authority prefix; admin/user views (and
        // the frontend) operate on plain role names such as "FACULTY_MENTOR".
        String role = stripRolePrefix(user.getPrimaryRole());

        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                role,
                user.getOrganizationId(),
                deadline,
                overdue,
                employeeId,
                contact
        );
    }

    private String resolveField(UUID userId, String fieldName, String plainValue,
                                java.util.function.Function<String, String> maskFn) {
        if (plainValue == null) {
            return null;
        }
        boolean granted = dataPermissionRepository.existsByUserIdAndFieldNameAndClassificationIn(
                userId, fieldName, UNMASKED_CLASSIFICATIONS);
        return granted ? plainValue : maskFn.apply(plainValue);
    }

    static Instant computeBusinessDayDeadline(Instant from, int businessDays) {
        ZonedDateTime current = from.atZone(ZoneOffset.UTC);
        int added = 0;
        while (added < businessDays) {
            current = current.plusDays(1);
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return current.toInstant();
    }
}
