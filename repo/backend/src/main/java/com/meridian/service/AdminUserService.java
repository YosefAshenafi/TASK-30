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

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            AuditService auditService,
                            DataPermissionRepository dataPermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
        this.dataPermissionRepository = dataPermissionRepository;
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
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> AppException.notFound("Role not found: " + roleName));

        String oldRole = user.getPrimaryRole();
        user.setRoles(Set.of(role));
        User saved = userRepository.save(user);
        log.info("Role changed: userId={}, oldRole={}, newRole={}", id, oldRole, roleName);

        auditService.logEvent(id, "PERMISSION_CHANGE", "USER", id.toString(),
                String.format("{\"oldRole\":\"%s\",\"newRole\":\"%s\"}", oldRole, roleName),
                null, null);

        return toSummaryDto(saved);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("User not found: " + id));
    }

    private UserSummaryDto toSummaryDto(User user) {
        Instant deadline = computeBusinessDayDeadline(user.getCreatedAt(), 2);
        boolean overdue = user.getStatus() == UserStatus.PENDING && Instant.now().isAfter(deadline);

        String employeeId = resolveField(user.getId(), "employee_id_enc", user.getEmployeeIdEnc(),
                FieldMaskingUtil::maskEmployeeId);
        String contact = resolveField(user.getId(), "contact_enc", user.getContactEnc(),
                FieldMaskingUtil::maskEmail);

        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.getPrimaryRole(),
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
