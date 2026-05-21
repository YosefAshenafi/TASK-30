package com.meridian.controller;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.dto.AnalyticsDto.ItemDifficultyItem;
import com.meridian.dto.AnalyticsDto.KnowledgeGapItem;
import com.meridian.dto.AnalyticsDto.MasteryTrendPoint;
import com.meridian.dto.AnalyticsDto.WrongAnswerItem;
import com.meridian.entity.User;
import com.meridian.service.AnalyticsService;
import com.meridian.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('FACULTY_MENTOR','CORPORATE_MENTOR','ADMINISTRATOR')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;

    public AnalyticsController(AnalyticsService analyticsService, ReportService reportService) {
        this.analyticsService = analyticsService;
        this.reportService = reportService;
    }

    @GetMapping("/mastery")
    public ResponseEntity<List<MasteryTrendPoint>> getMasteryTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(analyticsService.getMasteryTrends(filters));
    }

    @GetMapping("/wrong-answers")
    public ResponseEntity<List<WrongAnswerItem>> getWrongAnswers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(analyticsService.getWrongAnswerDistribution(filters));
    }

    @GetMapping("/knowledge-gaps")
    public ResponseEntity<List<KnowledgeGapItem>> getKnowledgeGaps(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(analyticsService.getKnowledgeGaps(filters));
    }

    @GetMapping("/item-difficulty")
    public ResponseEntity<List<ItemDifficultyItem>> getItemDifficulty(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(analyticsService.getItemDifficulty(filters));
    }

    @GetMapping("/learner/{userId}")
    public ResponseEntity<Map<String, Object>> getLearnerAnalytics(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, organizationId);
        String role = principal.getPrimaryRole();
        Map<String, Object> result = analyticsService.getLearnerAnalytics(
                userId, principal.getId(), role, principal.getOrganizationId(), filters);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cohort/{cohortId}")
    public ResponseEntity<Map<String, Object>> getCohortAnalytics(
            @PathVariable UUID cohortId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        Map<String, Object> result = Map.of(
                "masteryTrends", analyticsService.getMasteryTrends(filters),
                "wrongAnswers", analyticsService.getWrongAnswerDistribution(filters),
                "knowledgeGaps", analyticsService.getKnowledgeGaps(filters),
                "itemDifficulty", analyticsService.getItemDifficulty(filters)
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<Map<String, Object>> getCourseAnalytics(
            @PathVariable UUID courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, organizationId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        Map<String, Object> result = Map.of(
                "masteryTrends", analyticsService.getMasteryTrends(filters),
                "wrongAnswers", analyticsService.getWrongAnswerDistribution(filters),
                "knowledgeGaps", analyticsService.getKnowledgeGaps(filters),
                "itemDifficulty", analyticsService.getItemDifficulty(filters)
        );
        return ResponseEntity.ok(result);
    }
}
