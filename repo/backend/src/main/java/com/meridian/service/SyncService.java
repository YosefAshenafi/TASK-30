package com.meridian.service;

import com.meridian.dto.SessionDto.ActivityState;
import com.meridian.dto.SessionDto.SyncResult;
import com.meridian.dto.SessionDto.SyncSessionRequest;
import com.meridian.entity.SessionActivity;
import com.meridian.entity.TrainingSession;
import com.meridian.exception.AppException;
import com.meridian.repository.SessionActivityRepository;
import com.meridian.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final int MAX_BATCH_SIZE = 500;

    private final SessionRepository sessionRepository;
    private final SessionActivityRepository sessionActivityRepository;

    public SyncService(SessionRepository sessionRepository,
                       SessionActivityRepository sessionActivityRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionActivityRepository = sessionActivityRepository;
    }

    public SyncResult processBatch(UUID userId, List<SyncSessionRequest> requests) {
        if (requests.size() > MAX_BATCH_SIZE) {
            throw AppException.badRequest("Sync batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }

        int accepted = 0;
        int rejected = 0;
        int duplicates = 0;
        List<String> rejectedKeys = new ArrayList<>();

        for (SyncSessionRequest req : requests) {
            String key = req.idempotencyKey();

            Optional<TrainingSession> existing = sessionRepository.findByIdempotencyKey(key);

            if (existing.isPresent()) {
                TrainingSession existingSession = existing.get();

                if (!existingSession.getUserId().equals(userId)) {
                    rejected++;
                    rejectedKeys.add(key);
                    log.warn("Rejected sync for key {} — cross-user conflict", key);
                    continue;
                }

                if (req.clientUpdatedAt() != null
                        && req.clientUpdatedAt().isAfter(existingSession.getStartedAt())) {
                    applyUpdate(existingSession, req);
                    sessionRepository.save(existingSession);
                    accepted++;
                    log.debug("Updated session via sync key {}", key);
                } else {
                    duplicates++;
                    log.debug("Duplicate sync key {} — server version is newer or equal", key);
                }
            } else {
                TrainingSession session = buildNewSession(userId, req);
                TrainingSession saved = sessionRepository.save(session);
                syncActivities(saved.getId(), req.activities());
                accepted++;
                log.debug("Accepted new sync session with key {}", key);
            }
        }

        return new SyncResult(accepted, rejected, duplicates, rejectedKeys);
    }

    private TrainingSession buildNewSession(UUID userId, SyncSessionRequest req) {
        TrainingSession session = new TrainingSession();
        session.setUserId(userId);
        session.setCourseId(req.courseId());
        session.setIdempotencyKey(req.idempotencyKey());
        session.setCourseVersion(req.courseVersion());
        session.setRestTimerSecs(req.restTimerSecs());
        session.setSyncStatus(TrainingSession.SyncStatus.SYNCED);

        if (req.status() != null) {
            try {
                session.setStatus(TrainingSession.SessionStatus.valueOf(req.status()));
            } catch (IllegalArgumentException e) {
                session.setStatus(TrainingSession.SessionStatus.IN_PROGRESS);
            }
        }

        if (req.startedAt() != null) {
            session.setStartedAt(req.startedAt());
        }
        if (req.completedAt() != null) {
            session.setCompletedAt(req.completedAt());
        }

        return session;
    }

    private void applyUpdate(TrainingSession session, SyncSessionRequest req) {
        if (req.status() != null) {
            try {
                session.setStatus(TrainingSession.SessionStatus.valueOf(req.status()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        session.setRestTimerSecs(req.restTimerSecs());
        if (req.completedAt() != null) {
            session.setCompletedAt(req.completedAt());
        }
        session.setSyncStatus(TrainingSession.SyncStatus.SYNCED);
    }

    private void syncActivities(UUID sessionId, List<ActivityState> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        for (ActivityState state : activities) {
            SessionActivity activity = new SessionActivity();
            activity.setSessionId(sessionId);
            activity.setActivityRef(state.activityRef());
            activity.setCompleted(state.completed());
            if (state.completed()) {
                activity.setCompletedAt(java.time.Instant.now());
            }
            sessionActivityRepository.save(activity);
        }
    }
}
