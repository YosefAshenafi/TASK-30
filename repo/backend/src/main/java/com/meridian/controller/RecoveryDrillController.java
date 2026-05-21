package com.meridian.controller;

import com.meridian.dto.RecoveryDrillRequest;
import com.meridian.entity.RecoveryDrill;
import com.meridian.entity.User;
import com.meridian.repository.RecoveryDrillRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/recovery-drills")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class RecoveryDrillController {

    private final RecoveryDrillRepository drillRepository;

    public RecoveryDrillController(RecoveryDrillRepository drillRepository) {
        this.drillRepository = drillRepository;
    }

    @GetMapping
    public ResponseEntity<Page<RecoveryDrill>> listDrills(
            @PageableDefault(size = 20, sort = "drillDate") Pageable pageable) {
        return ResponseEntity.ok(drillRepository.findAllByOrderByDrillDateDesc(pageable));
    }

    @PostMapping
    public ResponseEntity<RecoveryDrill> recordDrill(
            @Valid @RequestBody RecoveryDrillRequest request,
            @AuthenticationPrincipal User principal) {

        RecoveryDrill drill = new RecoveryDrill();
        drill.setDrillDate(request.drillDate());
        drill.setPerformedBy(principal.getId());
        drill.setStepsCompleted(request.stepsCompleted());
        drill.setTotalSteps(request.totalSteps());
        drill.setOutcome(request.outcome());
        drill.setNotes(request.notes());

        RecoveryDrill saved = drillRepository.save(drill);
        return ResponseEntity.created(URI.create("/api/admin/recovery-drills/" + saved.getId()))
                .body(saved);
    }
}
