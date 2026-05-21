package com.meridian.controller;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.dto.ReportDto.ExportRequest;
import com.meridian.dto.ReportDto.ExportResponse;
import com.meridian.entity.Approval;
import com.meridian.entity.OperationalTransaction;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.ApprovalRepository;
import com.meridian.repository.OperationalTransactionRepository;
import com.meridian.service.AnomalyDetectionService;
import com.meridian.service.AuditService;
import com.meridian.service.ExportService;
import com.meridian.service.ReportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports/export")
@PreAuthorize("hasAnyRole('ADMINISTRATOR','FACULTY_MENTOR','CORPORATE_MENTOR')")
public class ReportExportController {

    private static final Logger log = LoggerFactory.getLogger(ReportExportController.class);

    private final ReportService reportService;
    private final ExportService exportService;
    private final AuditService auditService;
    private final ApprovalRepository approvalRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final OperationalTransactionRepository operationalTransactionRepository;

    public ReportExportController(ReportService reportService,
                                   ExportService exportService,
                                   AuditService auditService,
                                   ApprovalRepository approvalRepository,
                                   AnomalyDetectionService anomalyDetectionService,
                                   OperationalTransactionRepository operationalTransactionRepository) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.auditService = auditService;
        this.approvalRepository = approvalRepository;
        this.anomalyDetectionService = anomalyDetectionService;
        this.operationalTransactionRepository = operationalTransactionRepository;
    }

    @PostMapping
    public ResponseEntity<ExportResponse> exportReport(
            @Valid @RequestBody ExportRequest request,
            @AuthenticationPrincipal User principal) {

        UUID effectiveOrgId = reportService.resolveOrgScope(principal, request.orgId());

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));

        if (!isAdmin) {
            if (request.approvalId() == null) {
                throw AppException.badRequest("Export approval required. Submit an approval request first.");
            }
            Approval approval = approvalRepository.findById(request.approvalId())
                    .orElseThrow(() -> AppException.notFound("Approval not found: " + request.approvalId()));
            if (!"APPROVED".equals(approval.getStatus())) {
                throw AppException.forbidden("Export requires an approved approval record. Current status: " + approval.getStatus());
            }
        }

        anomalyDetectionService.enforceExportRateLimit(principal.getId());

        AnalyticsFilterParams filters = new AnalyticsFilterParams(
                null, null, null, null, null, effectiveOrgId);

        List<Map<String, Object>> data = fetchData(request.reportType(), effectiveOrgId, filters);

        Path exportPath;
        if ("PDF".equalsIgnoreCase(request.format())) {
            exportPath = exportService.exportPdf(data, request.reportType());
        } else {
            exportPath = exportService.exportCsv(data, request.reportType());
        }

        long sizeBytes;
        try {
            sizeBytes = Files.size(exportPath);
        } catch (Exception e) {
            log.warn("Could not determine file size for {}", exportPath);
            sizeBytes = 0L;
        }

        auditService.logEvent(principal.getId(), "EXPORT", "REPORT", request.reportType(),
                "{\"path\":\"" + exportPath + "\",\"format\":\"" + request.format() + "\"}",
                null, null);

        OperationalTransaction opTx = new OperationalTransaction();
        opTx.setTransactionType("EXPORT");
        opTx.setInitiatedBy(principal.getId());
        opTx.setEntityType("REPORT");
        opTx.setEntityId(request.reportType());
        opTx.setDetails("{\"format\":\"" + request.format() + "\",\"path\":\"" + exportPath + "\",\"sizeBytes\":" + sizeBytes + "}");
        operationalTransactionRepository.save(opTx);

        log.info("Export completed: user={} type={} format={} path={}",
                principal.getId(), request.reportType(), request.format(), exportPath);

        return ResponseEntity.ok(new ExportResponse(
                exportPath.toString(),
                exportPath.getFileName().toString(),
                sizeBytes
        ));
    }

    private List<Map<String, Object>> fetchData(String reportType, UUID orgId,
                                                  AnalyticsFilterParams filters) {
        if (reportType == null) return List.of();
        return switch (reportType.toUpperCase()) {
            case "ENROLLMENTS" -> reportService.getEnrollments(orgId, filters);
            case "SEAT_UTILIZATION" -> reportService.getSeatUtilization(orgId, filters);
            case "REFUNDS" -> reportService.getRefunds(orgId, filters);
            case "INVENTORY" -> reportService.getInventory(orgId, filters);
            case "CERTIFICATIONS_EXPIRING" -> reportService.getCertificationsExpiring(orgId, 30);
            default -> {
                log.warn("Unknown report type for export: {}", reportType);
                yield List.of();
            }
        };
    }
}
