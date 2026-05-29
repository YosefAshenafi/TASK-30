package com.meridian.controller;

import com.meridian.dto.ApprovalActionRequest;
import com.meridian.entity.Approval;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.ApprovalRepository;
import com.meridian.repository.UserRepository;
import com.meridian.service.ApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
@PreAuthorize("isAuthenticated()")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;

    public ApprovalController(ApprovalService approvalService,
                              ApprovalRepository approvalRepository,
                              UserRepository userRepository) {
        this.approvalService = approvalService;
        this.approvalRepository = approvalRepository;
        this.userRepository = userRepository;
    }

    record CreateApprovalRequest(
            @NotBlank String type,
            @NotBlank String entityType,
            @NotBlank String entityId
    ) {}

    /**
     * Creates a new approval request for the authenticated user.
     * Returns the persisted Approval in PENDING state.
     */
    @PostMapping
    public ResponseEntity<Approval> createApproval(
            @Valid @RequestBody CreateApprovalRequest request,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        Approval approval = approvalService.createApproval(
                currentUser.getId(), request.type(), request.entityType(), request.entityId());
        return ResponseEntity.status(HttpStatus.CREATED).body(approval);
    }

    /**
     * Returns approvals relevant to the current user.
     * Administrators see approvals where they are the approver (PENDING).
     * Other users see approvals they have requested.
     */
    @GetMapping
    public ResponseEntity<Page<Approval>> getApprovals(Authentication authentication, Pageable pageable) {
        User currentUser = resolveCurrentUser(authentication);
        boolean isAdmin = hasRole(currentUser, "ROLE_ADMINISTRATOR");

        Page<Approval> page;
        if (isAdmin) {
            page = approvalService.getApprovalsForApprover(currentUser.getId(), pageable);
        } else {
            page = approvalRepository.findByRequesterId(currentUser.getId(), pageable);
        }
        return ResponseEntity.ok(page);
    }

    /**
     * Approves a pending approval. Administrators only.
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Approval> approve(@PathVariable UUID id,
                                            @RequestBody(required = false) ApprovalActionRequest request,
                                            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        String notes = request != null ? request.notes() : null;
        Approval result = approvalService.approve(id, currentUser.getId(), notes);
        return ResponseEntity.ok(result);
    }

    /**
     * Rejects a pending approval. Administrators only.
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Approval> reject(@PathVariable UUID id,
                                           @RequestBody(required = false) ApprovalActionRequest request,
                                           Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        String notes = request != null ? request.notes() : null;
        Approval result = approvalService.reject(id, currentUser.getId(), notes);
        return ResponseEntity.ok(result);
    }

    private User resolveCurrentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> AppException.notFound("Authenticated user not found"));
    }

    private boolean hasRole(User user, String roleName) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleName));
    }
}
