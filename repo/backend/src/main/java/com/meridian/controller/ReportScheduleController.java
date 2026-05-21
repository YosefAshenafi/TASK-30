package com.meridian.controller;

import com.meridian.dto.ReportDto.CreateScheduleRequest;
import com.meridian.entity.ReportSchedule;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.ReportScheduleRepository;
import com.meridian.scheduler.jobs.ReportJob;
import jakarta.validation.Valid;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports/schedules")
@PreAuthorize("hasAnyRole('ADMINISTRATOR','CORPORATE_MENTOR')")
public class ReportScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleController.class);

    private final ReportScheduleRepository reportScheduleRepository;
    private final Scheduler scheduler;

    public ReportScheduleController(ReportScheduleRepository reportScheduleRepository,
                                     Scheduler scheduler) {
        this.reportScheduleRepository = reportScheduleRepository;
        this.scheduler = scheduler;
    }

    @PostMapping
    public ResponseEntity<ReportSchedule> createSchedule(
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal User principal) {

        ReportSchedule schedule = new ReportSchedule();
        schedule.setUserId(principal.getId());
        schedule.setOrganizationId(request.orgId());
        schedule.setReportType(request.reportType().toUpperCase());
        schedule.setCronExpression(request.cronExpression());
        schedule.setOutputFormat(request.outputFormat().toUpperCase());
        schedule.setOutputPath(request.outputPath());

        ReportSchedule saved = reportScheduleRepository.save(schedule);

        registerQuartzJob(saved);

        log.info("Created report schedule {} for user {}", saved.getId(), principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<Page<ReportSchedule>> listSchedules(
            @AuthenticationPrincipal User principal,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportSchedule> schedules;
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));

        if (isAdmin) {
            schedules = reportScheduleRepository.findByUserId(principal.getId(), pageable);
        } else {
            UUID orgId = principal.getOrganizationId();
            if (orgId != null) {
                schedules = reportScheduleRepository.findByOrganizationId(orgId, pageable);
            } else {
                schedules = reportScheduleRepository.findByUserId(principal.getId(), pageable);
            }
        }

        return ResponseEntity.ok(schedules);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal User principal) {

        ReportSchedule schedule = reportScheduleRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Report schedule not found: " + id));

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR"));

        if (!isAdmin && !schedule.getUserId().equals(principal.getId())) {
            throw AppException.forbidden("You do not have access to this schedule");
        }

        unregisterQuartzJob(id);
        reportScheduleRepository.delete(schedule);

        log.info("Deleted report schedule {} by user {}", id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    private void registerQuartzJob(ReportSchedule schedule) {
        try {
            String jobKeyStr = "report-" + schedule.getId();
            JobKey jobKey = new JobKey(jobKeyStr, "REPORTS");
            TriggerKey triggerKey = new TriggerKey(jobKeyStr, "REPORTS");

            JobDetail jobDetail = JobBuilder.newJob(ReportJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(ReportJob.KEY_REPORT_TYPE, schedule.getReportType())
                    .usingJobData(ReportJob.KEY_OUTPUT_FORMAT, schedule.getOutputFormat())
                    .usingJobData(ReportJob.KEY_ORG_ID,
                            schedule.getOrganizationId() != null ? schedule.getOrganizationId().toString() : null)
                    .usingJobData(ReportJob.KEY_SCHEDULE_ID, schedule.getId().toString())
                    .storeDurably()
                    .build();

            org.quartz.CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression()))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Registered Quartz job {} with cron {}", jobKeyStr, schedule.getCronExpression());
        } catch (SchedulerException e) {
            log.error("Failed to register Quartz job for schedule {}: {}", schedule.getId(), e.getMessage(), e);
            throw AppException.badRequest("Invalid cron expression or scheduler error: " + e.getMessage());
        }
    }

    private void unregisterQuartzJob(UUID scheduleId) {
        try {
            JobKey jobKey = new JobKey("report-" + scheduleId, "REPORTS");
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Unregistered Quartz job for schedule {}", scheduleId);
            }
        } catch (SchedulerException e) {
            log.warn("Failed to unregister Quartz job for schedule {}: {}", scheduleId, e.getMessage());
        }
    }
}
