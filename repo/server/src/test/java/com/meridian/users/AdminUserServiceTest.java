package com.meridian.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.approvals.entity.ApprovalRequest;
import com.meridian.approvals.repository.ApprovalRequestRepository;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.governance.MaskingPolicy;
import com.meridian.notifications.NotificationService;
import com.meridian.organizations.repository.OrganizationRepository;
import com.meridian.security.audit.AuditEvent;
import com.meridian.security.audit.AuditEventRepository;
import com.meridian.users.dto.UserSummaryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private MaskingPolicy maskingPolicy;

    @InjectMocks
    private AdminUserService service;

    @Test
    void approve_pendingUser_changesStatusToActive() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        User u = pendingUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.approve(id, actor);

        assertThat(u.getStatus()).isEqualTo("ACTIVE");
        verify(userRepository).save(u);
        verify(auditEventRepository).save(any(AuditEvent.class));
        verify(notificationService).send(eq(id), eq("approval.decided"), anyString());
    }

    @Test
    void approve_nonPending_throwsConflict() {
        UUID id = UUID.randomUUID();
        User u = pendingUser(id);
        u.setStatus("ACTIVE");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.approve(id, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approve_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(id, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reject_pendingUser_changesStatusToSuspended() {
        UUID id = UUID.randomUUID();
        User u = pendingUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.reject(id, UUID.randomUUID(), "bad email");

        assertThat(u.getStatus()).isEqualTo("SUSPENDED");
        verify(auditEventRepository).save(any(AuditEvent.class));
    }

    @Test
    void unlock_clearsCounterAndRestoresActive() {
        UUID id = UUID.randomUUID();
        User u = pendingUser(id);
        u.setStatus("LOCKED");
        u.setFailedLoginCount(5);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        service.unlock(id, UUID.randomUUID());

        assertThat(u.getStatus()).isEqualTo("ACTIVE");
        assertThat(u.getFailedLoginCount()).isZero();
        assertThat(u.getLockedUntil()).isNull();
    }

    @Test
    void validateStatus_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> service.validateStatus("NOT_A_STATUS"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validateStatus_valid_passes() {
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> service.validateStatus("ACTIVE"));
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> service.validateStatus("SUSPENDED"));
    }

    @Test
    void requestStatusChange_createsApproval() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        User u = pendingUser(id);
        u.setStatus("ACTIVE");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(approvalRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(inv -> {
                    ApprovalRequest ar = inv.getArgument(0);
                    ar.setId(UUID.randomUUID());
                    return ar;
                });

        ApprovalRequest ar = service.requestStatusChange(id, "SUSPENDED", actor);

        assertThat(ar.getType()).isEqualTo(AdminUserService.PERMISSION_CHANGE_TYPE);
        verify(auditEventRepository).save(any(AuditEvent.class));
    }

    @Test
    void listUsers_appliesRoleFilter() {
        User a = pendingUser(UUID.randomUUID());
        a.setRole("STUDENT");
        User b = pendingUser(UUID.randomUUID());
        b.setRole("ADMIN");
        when(userRepository.findByStatusFilter(null)).thenReturn(List.of(a, b));

        List<UserSummaryDto> result = service.listUsers(null, "STUDENT", null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("STUDENT");
    }

    @Test
    void applyApprovedStatusChange_validPayload_updatesStatus() {
        UUID targetId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        User u = pendingUser(targetId);
        u.setStatus("ACTIVE");
        when(userRepository.findById(targetId)).thenReturn(Optional.of(u));
        String payload = "{\"targetUserId\":\"" + targetId + "\",\"newStatus\":\"SUSPENDED\"}";

        service.applyApprovedStatusChange(payload, reviewer);

        assertThat(u.getStatus()).isEqualTo("SUSPENDED");
    }

    private User pendingUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id.toString().substring(0, 6));
        u.setDisplayName("User");
        u.setPasswordBcrypt("hash");
        u.setRole("STUDENT");
        u.setStatus("PENDING");
        return u;
    }
}
