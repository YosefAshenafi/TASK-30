package com.meridian.controller;

import com.meridian.entity.TrainingSession;
import com.meridian.entity.User;
import com.meridian.repository.AnomalyRepository;
import com.meridian.repository.ApprovalRepository;
import com.meridian.repository.SessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only landing-page summaries for the Angular dashboard. Returns lightweight
 * aggregate counts; detailed data is served by the dedicated analytics/report controllers.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final SessionRepository sessionRepository;
    private final ApprovalRepository approvalRepository;
    private final AnomalyRepository anomalyRepository;

    public DashboardController(SessionRepository sessionRepository,
                              ApprovalRepository approvalRepository,
                              AnomalyRepository anomalyRepository) {
        this.sessionRepository = sessionRepository;
        this.approvalRepository = approvalRepository;
        this.anomalyRepository = anomalyRepository;
    }

    /** Mentor/Administrator overview: system-wide counts. */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary() {
        DashboardSummary summary = new DashboardSummary(
                approvalRepository.countByStatus("PENDING"),
                anomalyRepository.count(),
                sessionRepository.count(),
                sessionRepository.countByStatus(TrainingSession.SessionStatus.IN_PROGRESS));
        return ResponseEntity.ok(summary);
    }

    /** Student overview: counts scoped to the authenticated user's own sessions. */
    @GetMapping("/student-summary")
    public ResponseEntity<DashboardSummary> studentSummary(
            @AuthenticationPrincipal UserDetails principal) {
        User user = (User) principal;
        DashboardSummary summary = new DashboardSummary(
                0L,
                0L,
                sessionRepository.countByUserId(user.getId()),
                sessionRepository.countByUserIdAndStatus(
                        user.getId(), TrainingSession.SessionStatus.IN_PROGRESS));
        return ResponseEntity.ok(summary);
    }

    public record DashboardSummary(
            long pendingApprovalsCount,
            long recentAnomaliesCount,
            long totalSessions,
            long activeSessions) {
    }
}
