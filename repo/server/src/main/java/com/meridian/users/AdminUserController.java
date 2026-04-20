package com.meridian.users;

import com.meridian.common.web.PageResponse;
import com.meridian.users.dto.RejectRequest;
import com.meridian.users.dto.StatusUpdateRequest;
import com.meridian.users.dto.UserSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<PageResponse<UserSummaryDto>> listUsers(
            @RequestParam(required = false) String status) {
        List<UserSummaryDto> users = adminUserService.listUsers(status);
        return ResponseEntity.ok(new PageResponse<>(users, 0, users.size(), users.size()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, Authentication auth) {
        adminUserService.approve(id, UUID.fromString(auth.getName()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody RejectRequest req,
                                       Authentication auth) {
        adminUserService.reject(id, UUID.fromString(auth.getName()), req.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id,
                                             @Valid @RequestBody StatusUpdateRequest req,
                                             Authentication auth) {
        adminUserService.updateStatus(id, req.status(), UUID.fromString(auth.getName()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable UUID id, Authentication auth) {
        adminUserService.unlock(id, UUID.fromString(auth.getName()));
        return ResponseEntity.noContent().build();
    }
}
