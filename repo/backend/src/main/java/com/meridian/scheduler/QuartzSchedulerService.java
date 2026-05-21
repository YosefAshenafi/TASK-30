package com.meridian.scheduler;

import com.meridian.scheduler.jobs.BackupJob;
import com.meridian.scheduler.jobs.ExportJob;
import jakarta.annotation.PostConstruct;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QuartzSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(QuartzSchedulerService.class);

    private static final String GROUP_REPORTS = "REPORTS";
    private static final String GROUP_BACKUP = "BACKUP";

    private final Scheduler scheduler;

    public QuartzSchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedules a recurring report-export job identified by {@code scheduleId}.
     *
     * @param scheduleId     UUID of the ReportSchedule record
     * @param cronExpression Quartz cron expression for the trigger
     * @param reportType     report type string placed in JobDataMap
     */
    public void scheduleReport(UUID scheduleId, String cronExpression, String reportType) {
        try {
            String jobKeyStr = scheduleId.toString();
            JobKey jobKey = JobKey.jobKey(jobKeyStr, GROUP_REPORTS);

            JobDataMap dataMap = new JobDataMap();
            dataMap.put(ExportJob.DATA_KEY_SCHEDULE_ID, scheduleId.toString());
            dataMap.put(ExportJob.DATA_KEY_REPORT_TYPE, reportType);

            JobDetail jobDetail = JobBuilder.newJob(ExportJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(dataMap)
                    .storeDurably(false)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(jobKeyStr, GROUP_REPORTS))
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled report job: scheduleId={} cron={} reportType={}",
                    scheduleId, cronExpression, reportType);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule report job: " + e.getMessage(), e);
        }
    }

    /**
     * Removes the scheduled report job for the given schedule ID.
     */
    public void deleteSchedule(UUID scheduleId) {
        try {
            JobKey jobKey = JobKey.jobKey(scheduleId.toString(), GROUP_REPORTS);
            boolean deleted = scheduler.deleteJob(jobKey);
            if (deleted) {
                log.info("Deleted scheduled report job: scheduleId={}", scheduleId);
            } else {
                log.warn("No scheduled report job found for scheduleId={}", scheduleId);
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to delete report schedule: " + e.getMessage(), e);
        }
    }

    /**
     * Registers the nightly incremental backup job (02:00 UTC) and the weekly full backup
     * job (03:00 UTC every Sunday) unless they are already scheduled.
     */
    @PostConstruct
    public void scheduleBackupJobs() {
        scheduleBackupIfAbsent(
                BackupJob.JOB_KEY_NIGHTLY,
                "INCREMENTAL",
                "0 0 2 * * ?");

        scheduleBackupIfAbsent(
                BackupJob.JOB_KEY_WEEKLY,
                "FULL",
                "0 0 3 ? * SUN");
    }

    private void scheduleBackupIfAbsent(String jobKeyStr, String type, String cronExpression) {
        try {
            JobKey jobKey = JobKey.jobKey(jobKeyStr, GROUP_BACKUP);

            if (scheduler.checkExists(jobKey)) {
                log.debug("Backup job already scheduled: key={}", jobKeyStr);
                return;
            }

            JobDataMap dataMap = new JobDataMap();
            dataMap.put(BackupJob.DATA_KEY_TYPE, type);

            JobDetail jobDetail = JobBuilder.newJob(BackupJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(dataMap)
                    .storeDurably(false)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TriggerKey.triggerKey(jobKeyStr, GROUP_BACKUP))
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Scheduled backup job: key={} type={} cron={}", jobKeyStr, type, cronExpression);
        } catch (SchedulerException e) {
            log.error("Failed to schedule backup job key={}: {}", jobKeyStr, e.getMessage(), e);
        }
    }
}
