package com.meridian.controller;

import com.meridian.dto.BackupDto;
import com.meridian.dto.TriggerBackupRequest;
import com.meridian.entity.Backup;
import com.meridian.service.BackupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Triggers an on-demand database backup and returns the backup record.
     */
    @PostMapping("/trigger")
    public ResponseEntity<BackupDto> triggerBackup(@Valid @RequestBody TriggerBackupRequest request) {
        Backup backup = backupService.triggerBackup(request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(BackupDto.from(backup));
    }

    /**
     * Returns a paginated backup history ordered by most recent first.
     */
    @GetMapping("/history")
    public ResponseEntity<Page<BackupDto>> getHistory(Pageable pageable) {
        return ResponseEntity.ok(backupService.getHistory(pageable).map(BackupDto::from));
    }
}
