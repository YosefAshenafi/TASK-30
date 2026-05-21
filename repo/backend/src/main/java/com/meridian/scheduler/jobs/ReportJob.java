package com.meridian.scheduler.jobs;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.service.ExportService;
import com.meridian.service.ReportService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@DisallowConcurrentExecution
public class ReportJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ReportJob.class);

    public static final String KEY_REPORT_TYPE = "reportType";
    public static final String KEY_OUTPUT_FORMAT = "outputFormat";
    public static final String KEY_ORG_ID = "orgId";
    public static final String KEY_SCHEDULE_ID = "scheduleId";

    private final ReportService reportService;
    private final ExportService exportService;

    public ReportJob(ReportService reportService, ExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        String reportType = data.getString(KEY_REPORT_TYPE);
        String outputFormat = data.getString(KEY_OUTPUT_FORMAT);
        String orgIdStr = data.getString(KEY_ORG_ID);
        String scheduleId = data.getString(KEY_SCHEDULE_ID);

        log.info("ReportJob starting: type={} format={} scheduleId={}", reportType, outputFormat, scheduleId);

        try {
            UUID orgId = orgIdStr != null ? UUID.fromString(orgIdStr) : null;
            AnalyticsFilterParams filters = new AnalyticsFilterParams(null, null, null, null, null, orgId);

            List<Map<String, Object>> reportData = fetchReportData(reportType, orgId, filters);

            Path outputPath;
            if ("PDF".equalsIgnoreCase(outputFormat)) {
                outputPath = exportService.exportPdf(reportData, reportType);
            } else {
                outputPath = exportService.exportCsv(reportData, reportType);
            }

            log.info("ReportJob completed: type={} path={} scheduleId={}", reportType, outputPath, scheduleId);
        } catch (Exception e) {
            log.error("ReportJob failed for type={} scheduleId={}: {}", reportType, scheduleId, e.getMessage(), e);
            throw new JobExecutionException("Report job failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> fetchReportData(String reportType, UUID orgId,
                                                       AnalyticsFilterParams filters) {
        if (reportType == null) return List.of();
        return switch (reportType.toUpperCase()) {
            case "ENROLLMENTS" -> reportService.getEnrollments(orgId, filters);
            case "SEAT_UTILIZATION" -> reportService.getSeatUtilization(orgId, filters);
            case "REFUNDS" -> reportService.getRefunds(orgId, filters);
            case "INVENTORY" -> reportService.getInventory(orgId, filters);
            case "CERTIFICATIONS_EXPIRING" -> reportService.getCertificationsExpiring(orgId, 30);
            default -> {
                log.warn("Unknown report type: {}", reportType);
                yield List.of();
            }
        };
    }
}
