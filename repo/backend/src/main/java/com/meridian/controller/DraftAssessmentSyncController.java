package com.meridian.controller;

import com.meridian.dto.DraftAssessmentSyncRequest;
import com.meridian.dto.SessionDto.SyncResult;
import com.meridian.entity.DraftAssessment;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.DraftAssessmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions/draft-assessments")
@PreAuthorize("isAuthenticated()")
public class DraftAssessmentSyncController {

    private static final int MAX_BATCH = 500;

    private final DraftAssessmentRepository draftAssessmentRepository;

    public DraftAssessmentSyncController(DraftAssessmentRepository draftAssessmentRepository) {
        this.draftAssessmentRepository = draftAssessmentRepository;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> syncDraftAssessments(
            @Valid @RequestBody List<DraftAssessmentSyncRequest> requests,
            @AuthenticationPrincipal User principal) {

        if (requests.size() > MAX_BATCH) {
            throw AppException.badRequest("Batch size exceeds maximum of " + MAX_BATCH);
        }

        int accepted = 0;
        int rejected = 0;
        int duplicates = 0;
        List<String> rejectedKeys = new ArrayList<>();

        for (DraftAssessmentSyncRequest req : requests) {
            String key = req.idempotencyKey();
            Optional<DraftAssessment> existing = draftAssessmentRepository.findByIdempotencyKey(key);

            if (existing.isPresent()) {
                DraftAssessment draft = existing.get();
                Instant clientTs = req.lastModified() != null ? req.lastModified() : Instant.EPOCH;

                if (clientTs.isAfter(draft.getLastModified())) {
                    draft.setAnswer(req.answer());
                    draft.setFlagged(req.flagged());
                    draft.setTimeSpentSecs(req.timeSpentSecs());
                    draft.setLastModified(clientTs);
                    draft.setSyncStatus(DraftAssessment.SyncStatus.SYNCED);
                    draftAssessmentRepository.save(draft);
                    accepted++;
                } else {
                    duplicates++;
                }
            } else {
                DraftAssessment draft = new DraftAssessment();
                draft.setIdempotencyKey(key);
                draft.setSessionId(req.sessionId());
                draft.setItemId(req.itemId());
                draft.setAnswer(req.answer());
                draft.setFlagged(req.flagged());
                draft.setTimeSpentSecs(req.timeSpentSecs());
                draft.setLastModified(req.lastModified() != null ? req.lastModified() : Instant.now());
                draft.setSyncStatus(DraftAssessment.SyncStatus.SYNCED);
                draftAssessmentRepository.save(draft);
                accepted++;
            }
        }

        return ResponseEntity.ok(new SyncResult(accepted, rejected, duplicates, rejectedKeys));
    }
}
