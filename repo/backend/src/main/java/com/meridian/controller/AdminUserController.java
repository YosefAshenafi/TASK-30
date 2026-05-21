package com.meridian.controller;

import com.meridian.dto.RoleChangeRequest;
import com.meridian.dto.UserSummaryDto;
import com.meridian.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users/pending")
    public ResponseEntity<List<UserSummaryDto>> getPendingUsers() {
        return ResponseEntity.ok(adminUserService.getPendingUsers());
    }

    @PutMapping("/users/{id}/approve")
    public ResponseEntity<UserSummaryDto> approveUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.approveUser(id));
    }

    @PutMapping("/users/{id}/reject")
    public ResponseEntity<UserSummaryDto> rejectUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.rejectUser(id));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryDto>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.getAllUsers(pageable));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserSummaryDto> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(adminUserService.changeRole(id, request.roleName()));
    }
}
