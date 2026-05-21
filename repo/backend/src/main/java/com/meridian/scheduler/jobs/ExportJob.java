package com.meridian.scheduler.jobs;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.entity.ReportSchedule;
import com.meridian.exception.AppException;
import com.meridian.repository.ReportScheduleRepository;
import com.meridian.service.ExportService;
import com.meridian.service.NotificationService;
import com.meridian.service.ReportService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quartz job that runs a scheduled report export.
 *
 * <p>The {@code JobDataMap} must contain:
 * <ul>
 *   <li>{@code scheduleId} – UUID of the {@link ReportSchedule} record</li>
 *   <li>{@code reportType} – report type string (e.g. "ENROLLMENTS")</li>
 * </ul>
 */
@Component
@DisallowConcurrentExecution
public class ExportJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ExportJob.class);

    public static final String DATA_KEY_SCHEDULE_ID = "scheduleId";
    public static final String DATA_KEY_REPORT_TYPE = "reportType";

    private final ReportScheduleRepository reportScheduleRepository;
    private final ReportService reportService;
    private final ExportService exportService;
    private final NotificationService notificationService;

    public ExportJob(ReportScheduleRepository reportScheduleRepository,
                     ReportService reportService,
                     ExportService exportService,
                     NotificationService notificationService) {
        this.reportScheduleRepository = reportScheduleRepository;
        this.reportService = reportService;
        this.exportService = exportService;
        this.notificationService = notificationService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String scheduleIdStr = context.getMergedJobDataMap().getString(DATA_KEY_SCHEDULE_ID);
        String reportType = context.getMergedJobDataMap().getString(DATA_KEY_REPORT_TYPE);

        if (scheduleIdStr == null || scheduleIdStr.isBlank()) {
            throw new JobExecutionException("scheduleId is missing from JobDataMap");
        }

        UUID scheduleId;
        try {
            scheduleId = UUID.fromString(scheduleIdStr);
        } catch (IllegalArgumentException e) {
            throw new JobExecutionException("Invalid scheduleId format: " + scheduleIdStr, e);
        }

        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new JobExecutionException(
                        "ReportSchedule not found for id: " + scheduleId));

        if (reportType == null || reportType.isBlank()) {
            reportType = schedule.getReportType();
        }

        log.info("ExportJob starting: scheduleId={} reportType={}", scheduleId, reportType);

        try {
            AnalyticsFilterParams filters = new AnalyticsFilterParams(null, null, null, null, null, null);
            List<Map<String, Object>> data = fetchReportData(reportType, schedule.getOrganizationId(), filters);

            Path exportPath;
            String format = schedule.getOutputFormat();
            if ("PDF".equalsIgnoreCase(format)) {
                exportPath = exportService.exportPdf(data, reportType);
            } else {
                exportPath = exportService.exportCsv(data, reportType);
            }

            schedule.setLastRunAt(Instant.now());
            reportScheduleRepository.save(schedule);

            notificationService.notify(schedule.getUserId(), "export_complete",
                    "Your scheduled export '" + reportType + "' is complete. File: " + exportPath);

            log.info("ExportJob completed: scheduleId={} path={}", scheduleId, exportPath);

        } catch (Exception e) {
            log.error("ExportJob failed: scheduleId={} reportType={}: {}", scheduleId, reportType, e.getMessage(), e);
            throw new JobExecutionException("Export failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> fetchReportData(String reportType,
                                                       UUID orgId,
                                                       AnalyticsFilterParams filters) {
        return switch (reportType.toUpperCase()) {
            case "ENROLLMENTS" -> reportService.getEnrollments(orgId, filters);
            case "SEAT_UTILIZATION" -> reportService.getSeatUtilization(orgId, filters);
            case "REFUNDS" -> reportService.getRefunds(orgId, filters);
            case "INVENTORY" -> reportService.getInventory(orgId, filters);
            default -> throw AppException.badRequest("Unknown report type: " + reportType);
        };
    }
}
