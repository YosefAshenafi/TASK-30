package com.meridian.service;

import com.meridian.entity.Approval;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.ApprovalRepository;
import com.meridian.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ApprovalService(ApprovalRepository approvalRepository,
                           UserRepository userRepository,
                           NotificationService notificationService,
                           AuditService auditService) {
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    /**
     * Creates a new PENDING approval request and notifies administrators.
     */
    public Approval createApproval(UUID requesterId, String type, String entityType, String entityId) {
        Approval approval = new Approval();
        approval.setRequesterId(requesterId);
        approval.setType(type);
        approval.setEntityType(entityType);
        approval.setEntityId(entityId);
        approval.setStatus("PENDING");
        Approval saved = approvalRepository.save(approval);

        notificationService.notifyRole("ROLE_ADMINISTRATOR", "approval_requested",
                "New approval request of type '" + type + "' for " + entityType + " " + entityId);

        return saved;
    }

    /**
     * Approves a pending approval. The approving user must be an administrator.
     */
    public Approval approve(UUID approvalId, UUID approverId, String notes) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> AppException.notFound("Approval not found: " + approvalId));

        verifyAdmin(approverId);

        approval.setApproverId(approverId);
        approval.setStatus("APPROVED");
        approval.setNotes(notes);
        approval.setResolvedAt(Instant.now());
        Approval saved = approvalRepository.save(approval);

        auditService.logEvent(approverId, "APPROVAL_GRANTED", "Approval",
                approvalId.toString(), "{\"status\":\"APPROVED\"}", null, null);

        return saved;
    }

    /**
     * Rejects a pending approval. The approving user must be an administrator.
     */
    public Approval reject(UUID approvalId, UUID approverId, String notes) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> AppException.notFound("Approval not found: " + approvalId));

        verifyAdmin(approverId);

        approval.setApproverId(approverId);
        approval.setStatus("REJECTED");
        approval.setNotes(notes);
        approval.setResolvedAt(Instant.now());
        Approval saved = approvalRepository.save(approval);

        auditService.logEvent(approverId, "APPROVAL_REJECTED", "Approval",
                approvalId.toString(), "{\"status\":\"REJECTED\"}", null, null);

        return saved;
    }

    /**
     * Returns pending approvals assigned to the given approver.
     */
    @Transactional(readOnly = true)
    public Page<Approval> getApprovalsForApprover(UUID approverId, Pageable pageable) {
        return approvalRepository.findByApproverIdAndStatus(approverId, "PENDING", pageable);
    }

    private void verifyAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found: " + userId));
        boolean isAdmin = user.getRoleNames().stream()
                .anyMatch(r -> r.equals("ROLE_ADMINISTRATOR") || r.equals("ADMINISTRATOR"));
        if (!isAdmin) {
            throw AppException.forbidden("Only administrators can perform this action");
        }
    }
}
