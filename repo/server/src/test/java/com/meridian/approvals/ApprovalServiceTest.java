package com.meridian.approvals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.approvals.entity.ApprovalRequest;
import com.meridian.approvals.repository.ApprovalRequestRepository;
import com.meridian.notifications.NotificationService;
import com.meridian.reports.entity.ReportRun;
import com.meridian.reports.repository.ReportRunRepository;
import com.meridian.reports.runner.ReportRunner;
import com.meridian.security.audit.AuditEventRepository;
import com.meridian.users.AdminUserService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRepository;
    @Mock
    private ReportRunRepository reportRunRepository;
    @Mock
    private ReportRunner reportRunner;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private AdminUserService adminUserService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ApprovalService service;

    @Test
    void approve_exportType_executesQueuedRuns() {
        UUID approvalId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ApprovalRequest ar = buildApproval(approvalId, "EXPORT", "PENDING");
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(ar));

        ReportRun run = new ReportRun();
        run.setId(UUID.randomUUID());
        when(reportRunRepository.findAllByApprovalRequestIdAndStatus(approvalId, "NEEDS_APPROVAL"))
                .thenReturn(List.of(run));

        ApprovalRequest result = service.approve(approvalId, reviewerId, "looks good");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getReviewedBy()).isEqualTo(reviewerId);
        verify(reportRunner).execute(run);
        verify(notificationService).send(eq(ar.getRequestedBy()), eq("approval.decided"), any());
    }

    @Test
    void approve_nonPending_throwsConflict() {
        UUID approvalId = UUID.randomUUID();
        ApprovalRequest ar = buildApproval(approvalId, "EXPORT", "APPROVED");
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(ar));

        assertThatThrownBy(() -> service.approve(approvalId, UUID.randomUUID(), "r"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approve_missing_throwsNotFound() {
        UUID approvalId = UUID.randomUUID();
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(approvalId, UUID.randomUUID(), "x"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reject_exportType_marksRunsAsFailed() {
        UUID approvalId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ApprovalRequest ar = buildApproval(approvalId, "EXPORT", "PENDING");
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(ar));

        ReportRun run = new ReportRun();
        run.setId(UUID.randomUUID());
        when(reportRunRepository.findAllByApprovalRequestIdAndStatus(approvalId, "NEEDS_APPROVAL"))
                .thenReturn(List.of(run));

        ApprovalRequest result = service.reject(approvalId, reviewerId, "no");

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(run.getStatus()).isEqualTo("FAILED");
        verify(reportRunRepository).save(run);
    }

    @Test
    void approve_permissionChangeType_appliesStatusChange() {
        UUID approvalId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ApprovalRequest ar = buildApproval(approvalId, AdminUserService.PERMISSION_CHANGE_TYPE, "PENDING");
        ar.setPayload("{\"action\":\"STATUS_UPDATE\",\"targetUserId\":\"" + UUID.randomUUID() + "\",\"newStatus\":\"SUSPENDED\"}");
        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(ar));

        service.approve(approvalId, reviewerId, null);

        verify(adminUserService).applyApprovedStatusChange(ar.getPayload(), reviewerId);
    }

    @Test
    void expireOldRequests_logsCountWhenPositive() {
        when(approvalRepository.expireOldRequests(any())).thenReturn(3);

        service.expireOldRequests();

        verify(approvalRepository).expireOldRequests(any());
    }

    private ApprovalRequest buildApproval(UUID id, String type, String status) {
        ApprovalRequest ar = new ApprovalRequest();
        ar.setId(id);
        ar.setType(type);
        ar.setStatus(status);
        ar.setRequestedBy(UUID.randomUUID());
        ar.setPayload("{}");
        return ar;
    }
}
