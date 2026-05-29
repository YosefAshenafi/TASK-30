package com.meridian.service;

import com.meridian.dto.SessionDto.ActivityUpdate;
import com.meridian.dto.SessionDto.CreateSessionRequest;
import com.meridian.dto.SessionDto.UpdateSessionRequest;
import com.meridian.entity.AssessmentItem;
import com.meridian.entity.SessionActivity;
import com.meridian.entity.TrainingSession;
import com.meridian.exception.AppException;
import com.meridian.repository.AssessmentItemRepository;
import com.meridian.repository.CourseRepository;
import com.meridian.repository.SessionActivityRepository;
import com.meridian.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final int MIN_REST_TIMER = 15;
    private static final int MAX_REST_TIMER = 300;
    private static final int DEFAULT_REST_TIMER = 60;

    private final SessionRepository sessionRepository;
    private final SessionActivityRepository sessionActivityRepository;
    private final CourseRepository courseRepository;
    private final AssessmentItemRepository assessmentItemRepository;

    public SessionService(SessionRepository sessionRepository,
                          SessionActivityRepository sessionActivityRepository,
                          CourseRepository courseRepository,
                          AssessmentItemRepository assessmentItemRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionActivityRepository = sessionActivityRepository;
        this.courseRepository = courseRepository;
        this.assessmentItemRepository = assessmentItemRepository;
    }

    public TrainingSession createSession(UUID userId, CreateSessionRequest req) {
        courseRepository.findById(req.courseId())
                .orElseThrow(() -> AppException.notFound("Course not found: " + req.courseId()));

        int restTimer = DEFAULT_REST_TIMER;
        if (req.restTimerSecs() != null) {
            restTimer = req.restTimerSecs();
            validateRestTimer(restTimer);
        }

        TrainingSession session = new TrainingSession();
        session.setUserId(userId);
        session.setCourseId(req.courseId());
        session.setRestTimerSecs(restTimer);
        session.setStatus(TrainingSession.SessionStatus.IN_PROGRESS);
        session.setSyncStatus(TrainingSession.SyncStatus.SYNCED);

        TrainingSession saved = sessionRepository.save(session);

        // Seed the per-session activity checklist from the course's assessment items
        // so the student has concrete activities to complete and check off.
        List<AssessmentItem> items = assessmentItemRepository.findByCourseId(req.courseId());
        for (AssessmentItem item : items) {
            SessionActivity activity = new SessionActivity();
            activity.setSessionId(saved.getId());
            activity.setActivityRef(item.getQuestion());
            activity.setCompleted(false);
            sessionActivityRepository.save(activity);
        }

        log.info("Created training session {} for user {} with {} activities",
                saved.getId(), userId, items.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<TrainingSession> getSessionsByUser(UUID userId, Pageable pageable) {
        return sessionRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public TrainingSession getSessionById(UUID sessionId, UUID userId) {
        TrainingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw AppException.forbidden("You do not have access to session: " + sessionId);
        }
        return session;
    }

    public TrainingSession updateSession(UUID sessionId, UUID userId, UpdateSessionRequest req) {
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> AppException.forbidden("Session not found or access denied: " + sessionId));

        if (req.restTimerSecs() != null) {
            validateRestTimer(req.restTimerSecs());
            session.setRestTimerSecs(req.restTimerSecs());
        }

        if (req.activities() != null && !req.activities().isEmpty()) {
            for (ActivityUpdate update : req.activities()) {
                sessionActivityRepository.findById(update.activityId()).ifPresent(activity -> {
                    if (activity.getSessionId().equals(sessionId)) {
                        activity.setCompleted(update.completed());
                        activity.setCompletedAt(update.completed() ? Instant.now() : null);
                        sessionActivityRepository.save(activity);
                    }
                });
            }
        }

        TrainingSession saved = sessionRepository.save(session);
        log.info("Updated session {} for user {}", sessionId, userId);
        return saved;
    }

    public TrainingSession completeSession(UUID sessionId, UUID userId) {
        TrainingSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> AppException.forbidden("Session not found or access denied: " + sessionId));

        session.setStatus(TrainingSession.SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());

        TrainingSession saved = sessionRepository.save(session);
        log.info("Completed session {} for user {}", sessionId, userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SessionActivity> getActivities(UUID sessionId, UUID userId) {
        sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> AppException.forbidden("Session not found or access denied: " + sessionId));
        return sessionActivityRepository.findBySessionId(sessionId);
    }

    private void validateRestTimer(int value) {
        if (value < MIN_REST_TIMER || value > MAX_REST_TIMER) {
            throw AppException.badRequest(
                    "restTimerSecs must be between " + MIN_REST_TIMER + " and " + MAX_REST_TIMER);
        }
    }
}
