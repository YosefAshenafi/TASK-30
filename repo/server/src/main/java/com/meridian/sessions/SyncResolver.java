package com.meridian.sessions;

import com.meridian.common.idempotency.IdempotencyService;
import com.meridian.sessions.dto.SyncRequest;
import com.meridian.sessions.dto.SyncResult;
import com.meridian.sessions.dto.TrainingSessionDto;
import com.meridian.sessions.dto.SessionSetDto;
import com.meridian.sessions.entity.SessionActivitySet;
import com.meridian.sessions.entity.TrainingSession;
import com.meridian.sessions.repository.SessionActivitySetRepository;
import com.meridian.sessions.repository.TrainingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncResolver {

    private final TrainingSessionRepository sessionRepo;
    private final SessionActivitySetRepository setRepo;
    private final IdempotencyService idempotencyService;

    @Transactional
    public SyncResult resolve(SyncRequest req, UUID userId) {
        List<SyncResult.AppliedItem> applied = new ArrayList<>();
        List<SyncResult.ConflictItem> conflicts = new ArrayList<>();
        Set<UUID> verifiedSessionIds = new HashSet<>();

        for (SyncRequest.SessionSyncItem item : req.sessions()) {
            String hash = idempotencyService.hashBody(item.toString());

            Optional<TrainingSessionDto> cached;
            try {
                cached = idempotencyService.check(item.idempotencyKey(), hash, TrainingSessionDto.class);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    Optional<TrainingSession> existing = sessionRepo.findByIdAndDeletedAtIsNull(item.id());
                    Object serverVer = existing.map(SessionMapper::toDto).orElse(null);
                    conflicts.add(new SyncResult.ConflictItem(item.id(), "IDEMPOTENCY_MISMATCH", serverVer));
                    continue;
                }
                throw e;
            }

            if (cached.isPresent()) {
                applied.add(new SyncResult.AppliedItem(item.id(), "session", "NOOP"));
                continue;
            }

            Optional<TrainingSession> existingOpt = sessionRepo.findByIdAndDeletedAtIsNull(item.id());
            if (existingOpt.isPresent()) {
                TrainingSession existing = existingOpt.get();
                if (!existing.getStudentId().equals(userId)) {
                    conflicts.add(new SyncResult.ConflictItem(item.id(), "ACCESS_DENIED", null));
                    continue;
                }
                if (item.clientUpdatedAt().isBefore(existing.getClientUpdatedAt())) {
                    conflicts.add(new SyncResult.ConflictItem(item.id(), "OLDER_CLIENT_TIMESTAMP",
                            SessionMapper.toDto(existing)));
                    continue;
                }
                applySessionUpdate(existing, item);
                sessionRepo.save(existing);
                TrainingSessionDto dto = SessionMapper.toDto(existing);
                idempotencyService.store(item.idempotencyKey(), userId, hash, dto);
                verifiedSessionIds.add(item.id());
                applied.add(new SyncResult.AppliedItem(item.id(), "session", "UPDATED"));
            } else {
                TrainingSession session = createSession(item, userId);
                sessionRepo.save(session);
                TrainingSessionDto dto = SessionMapper.toDto(session);
                idempotencyService.store(item.idempotencyKey(), userId, hash, dto);
                verifiedSessionIds.add(item.id());
                applied.add(new SyncResult.AppliedItem(item.id(), "session", "CREATED"));
            }
        }

        for (SyncRequest.SetSyncItem item : req.sets()) {
            String hash = idempotencyService.hashBody(item.toString());

            Optional<SessionSetDto> cached;
            try {
                cached = idempotencyService.check(item.idempotencyKey(), hash, SessionSetDto.class);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    Optional<SessionActivitySet> existing = setRepo.findBySessionIdAndActivityIdAndSetIndex(
                            item.sessionId(), item.activityId(), item.setIndex());
                    Object serverVer = existing.map(SessionMapper::toSetDto).orElse(null);
                    conflicts.add(new SyncResult.ConflictItem(item.sessionId(), "IDEMPOTENCY_MISMATCH", serverVer));
                    continue;
                }
                throw e;
            }

            if (cached.isPresent()) {
                applied.add(new SyncResult.AppliedItem(item.sessionId(), "set", "NOOP"));
                continue;
            }

            if (!verifiedSessionIds.contains(item.sessionId())) {
                Optional<TrainingSession> sess = sessionRepo.findByIdAndDeletedAtIsNull(item.sessionId());
                if (sess.isEmpty() || !sess.get().getStudentId().equals(userId)) {
                    conflicts.add(new SyncResult.ConflictItem(item.sessionId(), "ACCESS_DENIED", null));
                    continue;
                }
                verifiedSessionIds.add(item.sessionId());
            }

            Optional<SessionActivitySet> existingOpt = setRepo.findBySessionIdAndActivityIdAndSetIndex(
                    item.sessionId(), item.activityId(), item.setIndex());

            if (existingOpt.isPresent()) {
                SessionActivitySet existing = existingOpt.get();
                if (item.clientUpdatedAt().isBefore(existing.getClientUpdatedAt())) {
                    conflicts.add(new SyncResult.ConflictItem(existing.getId(), "OLDER_CLIENT_TIMESTAMP",
                            SessionMapper.toSetDto(existing)));
                    continue;
                }
                applySetUpdate(existing, item);
                setRepo.save(existing);
                SessionSetDto dto = SessionMapper.toSetDto(existing);
                idempotencyService.store(item.idempotencyKey(), userId, hash, dto);
                applied.add(new SyncResult.AppliedItem(existing.getId(), "set", "UPDATED"));
            } else {
                SessionActivitySet set = createSet(item);
                setRepo.save(set);
                SessionSetDto dto = SessionMapper.toSetDto(set);
                idempotencyService.store(item.idempotencyKey(), userId, hash, dto);
                applied.add(new SyncResult.AppliedItem(set.getId(), "set", "CREATED"));
            }
        }

        return new SyncResult(applied, conflicts);
    }

    private TrainingSession createSession(SyncRequest.SessionSyncItem item, UUID userId) {
        TrainingSession s = new TrainingSession();
        s.setId(item.id());
        s.setStudentId(userId);
        s.setCourseId(item.courseId());
        s.setCohortId(item.cohortId());
        s.setRestSecondsDefault(item.restSecondsDefault());
        s.setStatus(item.status() != null ? item.status() : "IN_PROGRESS");
        s.setStartedAt(item.startedAt() != null ? item.startedAt() : Instant.now());
        s.setEndedAt(item.endedAt());
        s.setClientUpdatedAt(item.clientUpdatedAt());
        return s;
    }

    private void applySessionUpdate(TrainingSession s, SyncRequest.SessionSyncItem item) {
        if (item.status() != null) s.setStatus(item.status());
        s.setEndedAt(item.endedAt());
        s.setClientUpdatedAt(item.clientUpdatedAt());
    }

    private SessionActivitySet createSet(SyncRequest.SetSyncItem item) {
        SessionActivitySet s = new SessionActivitySet();
        s.setSessionId(item.sessionId());
        s.setActivityId(item.activityId());
        s.setSetIndex(item.setIndex());
        s.setRestSeconds(item.restSeconds());
        s.setCompletedAt(item.completedAt());
        s.setNotes(item.notes());
        s.setClientUpdatedAt(item.clientUpdatedAt());
        return s;
    }

    private void applySetUpdate(SessionActivitySet s, SyncRequest.SetSyncItem item) {
        s.setRestSeconds(item.restSeconds());
        s.setCompletedAt(item.completedAt());
        s.setNotes(item.notes());
        s.setClientUpdatedAt(item.clientUpdatedAt());
    }
}
