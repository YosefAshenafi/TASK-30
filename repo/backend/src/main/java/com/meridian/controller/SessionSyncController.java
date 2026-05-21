package com.meridian.controller;

import com.meridian.dto.SessionDto.SyncResult;
import com.meridian.dto.SessionDto.SyncSessionRequest;
import com.meridian.service.SyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("isAuthenticated()")
@Validated
public class SessionSyncController {

    private final SyncService syncService;

    public SessionSyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> syncSessions(
            @Valid @RequestBody @Size(max = 500, message = "Batch size must not exceed 500")
            List<@Valid SyncSessionRequest> requests,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        SyncResult result = syncService.processBatch(user.getId(), requests);
        return ResponseEntity.ok(result);
    }
}
