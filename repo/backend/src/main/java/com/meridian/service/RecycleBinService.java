package com.meridian.service;

import com.meridian.entity.RecycleBinEntry;
import com.meridian.entity.User;
import com.meridian.exception.AppException;
import com.meridian.repository.RecycleBinRepository;
import com.meridian.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RecycleBinService {

    private static final Logger log = LoggerFactory.getLogger(RecycleBinService.class);
    private static final long DEFAULT_EXPIRY_DAYS = 14;

    private final RecycleBinRepository recycleBinRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RecycleBinService(RecycleBinRepository recycleBinRepository,
                              UserRepository userRepository,
                              AuditService auditService) {
        this.recycleBinRepository = recycleBinRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Soft-deletes an entity by placing it in the recycle bin.
     *
     * @param entityType       the logical type name of the entity (e.g. "User", "Course")
     * @param entityId         the UUID of the entity being deleted
     * @param originalDataJson JSON serialization of the entity at the time of deletion
     * @param deletedBy        UUID of the user performing the deletion
     * @return the created RecycleBinEntry
     */
    public RecycleBinEntry softDelete(String entityType,
                                      UUID entityId,
                                      String originalDataJson,
                                      UUID deletedBy) {
        RecycleBinEntry entry = new RecycleBinEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOriginalData(originalDataJson);
        entry.setDeletedBy(deletedBy);
        entry.setExpiresAt(Instant.now().plusSeconds(DEFAULT_EXPIRY_DAYS * 86400));
        RecycleBinEntry saved = recycleBinRepository.save(entry);

        auditService.logEvent(deletedBy, "DATA_DELETE", entityType, entityId.toString(),
                "{\"recycleBinId\":\"" + saved.getId() + "\"}", null, null);

        log.info("Soft deleted {} id={} by user={}", entityType, entityId, deletedBy);
        return saved;
    }

    /**
     * Restores an item from the recycle bin. The caller is responsible for actually
     * reinserting the entity data. This method validates admin access, emits an audit
     * event, deletes the recycle bin entry, and returns it so the caller can use
     * {@link RecycleBinEntry#getOriginalData()} to restore the entity.
     *
     * @param recycleBinId       UUID of the recycle bin entry
     * @param requestingUserId   UUID of the user requesting the restore
     * @return the RecycleBinEntry that was removed from the recycle bin
     */
    public RecycleBinEntry restore(UUID recycleBinId, UUID requestingUserId) {
        RecycleBinEntry entry = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> AppException.notFound("Recycle bin entry not found: " + recycleBinId));

        verifyAdmin(requestingUserId);

        auditService.logEvent(requestingUserId, "RESTORE", entry.getEntityType(),
                entry.getEntityId().toString(),
                "{\"recycleBinId\":\"" + recycleBinId + "\"}", null, null);

        recycleBinRepository.delete(entry);
        log.info("Restored {} id={} from recycle bin by user={}",
                entry.getEntityType(), entry.getEntityId(), requestingUserId);
        return entry;
    }

    /**
     * Permanently removes a recycle bin entry. Verifies admin access.
     */
    public void hardDelete(UUID recycleBinId, UUID requestingUserId) {
        RecycleBinEntry entry = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> AppException.notFound("Recycle bin entry not found: " + recycleBinId));

        verifyAdmin(requestingUserId);

        recycleBinRepository.delete(entry);

        auditService.logEvent(requestingUserId, "DATA_DELETE", entry.getEntityType(),
                entry.getEntityId().toString(),
                "{\"recycleBinId\":\"" + recycleBinId + "\",\"permanent\":true}", null, null);

        log.info("Hard deleted recycle bin entry {} by user={}", recycleBinId, requestingUserId);
    }

    /**
     * Purges all recycle bin entries whose expiry timestamp has passed.
     * Intended to be called by a scheduled Quartz job.
     */
    public void purgeExpired() {
        List<RecycleBinEntry> expired = recycleBinRepository.findByExpiresAtBefore(Instant.now());
        for (RecycleBinEntry entry : expired) {
            try {
                recycleBinRepository.delete(entry);
                log.info("Purged expired recycle bin entry: id={} entityType={} entityId={}",
                        entry.getId(), entry.getEntityType(), entry.getEntityId());
            } catch (Exception e) {
                log.warn("Error purging recycle bin entry id={}: {}", entry.getId(), e.getMessage());
            }
        }
    }

    private void verifyAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found: " + userId));
        boolean isAdmin = user.getRoleNames().stream()
                .anyMatch(r -> r.equals("ROLE_ADMINISTRATOR") || r.equals("ADMINISTRATOR"));
        if (!isAdmin) {
            throw AppException.forbidden("Only administrators can perform this action");
        }
    }
}
