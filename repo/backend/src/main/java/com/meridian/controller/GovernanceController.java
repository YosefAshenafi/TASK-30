package com.meridian.controller;

import com.meridian.dto.PermissionUpdateRequest;
import com.meridian.entity.Approval;
import com.meridian.entity.DataPermission;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.DataPermissionRepository;
import com.meridian.repository.UserRepository;
import com.meridian.service.ApprovalService;
import com.meridian.service.AuditService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/governance")
@PreAuthorize("isAuthenticated()")
public class GovernanceController {

    private final DataPermissionRepository dataPermissionRepository;
    private final UserRepository userRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;

    public GovernanceController(DataPermissionRepository dataPermissionRepository,
                                UserRepository userRepository,
                                ApprovalService approvalService,
                                AuditService auditService) {
        this.dataPermissionRepository = dataPermissionRepository;
        this.userRepository = userRepository;
        this.approvalService = approvalService;
        this.auditService = auditService;
    }

    /**
     * Returns the data permissions for a given user.
     * Allowed if the requester is an ADMINISTRATOR or is requesting their own permissions.
     */
    @GetMapping("/users/{id}/permissions")
    public ResponseEntity<List<DataPermission>> getUserPermissions(
            @PathVariable UUID id,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        boolean isAdmin = hasRole(currentUser, "ROLE_ADMINISTRATOR");

        if (!isAdmin && !currentUser.getId().equals(id)) {
            throw AppException.forbidden("Access denied: you can only view your own permissions");
        }

        List<DataPermission> permissions = dataPermissionRepository.findByUserId(id);
        return ResponseEntity.ok(permissions);
    }

    /**
     * Updates (or creates) the data permission for a field on a user.
     * RESTRICTED classification requires an approval workflow; all others are applied directly.
     * Administrators only.
     */
    @PutMapping("/users/{id}/permissions")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<?> updateUserPermission(
            @PathVariable UUID id,
            @Valid @RequestBody PermissionUpdateRequest request,
            Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);

        if ("RESTRICTED".equals(request.classification())) {
            Approval approval = approvalService.createApproval(
                    currentUser.getId(), "PERMISSION_CHANGE", "DataPermission", id.toString());
            auditService.logEvent(currentUser.getId(), "PERMISSION_CHANGE", "DataPermission",
                    id.toString(),
                    "{\"fieldName\":\"" + request.fieldName() + "\",\"classification\":\"" + request.classification() + "\",\"pendingApproval\":true}",
                    null, null);
            return ResponseEntity.accepted().body(approval);
        }

        DataPermission permission = dataPermissionRepository
                .findByUserIdAndFieldName(id, request.fieldName())
                .orElse(new DataPermission());

        permission.setUserId(id);
        permission.setFieldName(request.fieldName());
        permission.setClassification(request.classification());
        permission.setGrantedBy(currentUser.getId());
        DataPermission saved = dataPermissionRepository.save(permission);

        auditService.logEvent(currentUser.getId(), "PERMISSION_CHANGE", "DataPermission",
                id.toString(),
                "{\"fieldName\":\"" + request.fieldName() + "\",\"classification\":\"" + request.classification() + "\"}",
                null, null);

        return ResponseEntity.ok(saved);
    }

    private User resolveCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> AppException.notFound("Authenticated user not found: " + username));
    }

    private boolean hasRole(User user, String roleName) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(roleName));
    }
}
