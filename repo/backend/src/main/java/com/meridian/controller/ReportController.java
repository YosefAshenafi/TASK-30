package com.meridian.controller;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.entity.User;
import com.meridian.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMINISTRATOR','FACULTY_MENTOR','CORPORATE_MENTOR')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/enrollments")
    public ResponseEntity<List<Map<String, Object>>> getEnrollments(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, orgId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(reportService.getEnrollments(effectiveOrgId, filters));
    }

    @GetMapping("/seat-utilization")
    public ResponseEntity<List<Map<String, Object>>> getSeatUtilization(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, orgId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(reportService.getSeatUtilization(effectiveOrgId, filters));
    }

    @GetMapping("/refunds")
    public ResponseEntity<List<Map<String, Object>>> getRefunds(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, orgId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(reportService.getRefunds(effectiveOrgId, filters));
    }

    @GetMapping("/inventory")
    public ResponseEntity<List<Map<String, Object>>> getInventory(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(required = false) String courseVersion,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, orgId);
        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                dateFrom, dateTo, location, instructorId, courseVersion, effectiveOrgId);
        return ResponseEntity.ok(reportService.getInventory(effectiveOrgId, filters));
    }

    @GetMapping("/certifications/expiring")
    public ResponseEntity<List<Map<String, Object>>> getCertificationsExpiring(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal User principal) {

        if (days != 30 && days != 60 && days != 90) {
            days = 30;
        }
        UUID effectiveOrgId = reportService.resolveOrgScope(principal, orgId);
        return ResponseEntity.ok(reportService.getCertificationsExpiring(effectiveOrgId, days));
    }
}
