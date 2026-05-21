package com.meridian.scheduler.jobs;

import com.meridian.entity.Backup;
import com.meridian.service.BackupService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Quartz job for scheduled database backups.
 *
 * <p>Two job keys are used by the scheduler:
 * <ul>
 *   <li>{@code NIGHTLY_INCREMENTAL} – runs nightly, performs an incremental (schema-only) dump</li>
 *   <li>{@code WEEKLY_FULL} – runs weekly on Sunday, performs a full custom-format dump</li>
 * </ul>
 * The job type is carried in the {@code JobDataMap} under the key {@code "type"}.
 */
@Component
@DisallowConcurrentExecution
public class BackupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(BackupJob.class);

    public static final String JOB_KEY_NIGHTLY = "NIGHTLY_INCREMENTAL";
    public static final String JOB_KEY_WEEKLY = "WEEKLY_FULL";
    public static final String DATA_KEY_TYPE = "type";

    private final BackupService backupService;

    public BackupJob(BackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String type = context.getMergedJobDataMap().getString(DATA_KEY_TYPE);
        if (type == null || type.isBlank()) {
            type = "INCREMENTAL";
        }
        log.info("BackupJob starting: type={}", type);
        try {
            Backup backup = backupService.triggerBackup(type);
            log.info("BackupJob completed: id={} path={} sizeBytes={}",
                    backup.getId(), backup.getPath(), backup.getSizeBytes());
        } catch (Exception e) {
            log.error("BackupJob failed for type={}: {}", type, e.getMessage(), e);
            throw new JobExecutionException("Backup failed: " + e.getMessage(), e);
        }
    }
}
