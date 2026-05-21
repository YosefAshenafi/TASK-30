package com.meridian.controller;

import com.meridian.entity.RecycleBinEntry;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.RecycleBinRepository;
import com.meridian.repository.UserRepository;
import com.meridian.service.RecycleBinService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/recycle-bin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;
    private final RecycleBinRepository recycleBinRepository;
    private final UserRepository userRepository;

    public RecycleBinController(RecycleBinService recycleBinService,
                                 RecycleBinRepository recycleBinRepository,
                                 UserRepository userRepository) {
        this.recycleBinService = recycleBinService;
        this.recycleBinRepository = recycleBinRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns a paginated list of recycle bin entries, optionally filtered by entity type.
     */
    @GetMapping
    public ResponseEntity<Page<RecycleBinEntry>> list(
            @RequestParam(required = false) String entityType,
            Pageable pageable) {
        Page<RecycleBinEntry> page;
        if (entityType != null && !entityType.isBlank()) {
            page = recycleBinRepository.findByEntityType(entityType, pageable);
        } else {
            page = recycleBinRepository.findAll(pageable);
        }
        return ResponseEntity.ok(page);
    }

    /**
     * Restores an entity from the recycle bin. Returns the recycle bin entry with the
     * original JSON so the caller can re-create the entity if needed.
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<RecycleBinEntry> restore(@PathVariable UUID id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        RecycleBinEntry entry = recycleBinService.restore(id, currentUser.getId());
        return ResponseEntity.ok(entry);
    }

    /**
     * Permanently removes a recycle bin entry without restoring the underlying entity.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID id, Authentication authentication) {
        User currentUser = resolveCurrentUser(authentication);
        recycleBinService.hardDelete(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    private User resolveCurrentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> AppException.notFound("Authenticated user not found"));
    }
}
