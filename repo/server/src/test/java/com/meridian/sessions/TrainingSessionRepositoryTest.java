package com.meridian.sessions;

import com.meridian.sessions.entity.TrainingSession;
import com.meridian.sessions.repository.TrainingSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct behavior tests for {@link TrainingSessionRepository}.
 *
 * Pins the invariants the repository is relied on for:
 *  - soft-delete-aware lookup ({@code findByIdAndDeletedAtIsNull})
 *  - filtered queries honoring null-safe parameter handling
 *  - pagination returns the requested size window
 *
 * Uses the real Testcontainers Postgres backing the {@code test} profile,
 * since the query is hand-written native SQL with Postgres-specific casts.
 */
@SpringBootTest(classes = com.meridian.MeridianApplication.class)
@ActiveProfiles("test")
class TrainingSessionRepositoryTest {

    @Autowired
    private TrainingSessionRepository repo;

    private UUID aliveId;
    private UUID softDeletedId;
    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");

    @BeforeEach
    void seed() {
        aliveId = UUID.randomUUID();
        softDeletedId = UUID.randomUUID();
        repo.save(makeSession(aliveId, STUDENT_ID, "IN_PROGRESS", null));
        repo.save(makeSession(softDeletedId, STUDENT_ID, "IN_PROGRESS", Instant.now()));
    }

    @AfterEach
    void cleanup() {
        repo.deleteById(aliveId);
        repo.deleteById(softDeletedId);
    }

    @Test
    void findByIdAndDeletedAtIsNull_returnsOnlyAliveSession() {
        Optional<TrainingSession> alive = repo.findByIdAndDeletedAtIsNull(aliveId);
        assertThat(alive).isPresent();
        assertThat(alive.get().getId()).isEqualTo(aliveId);
        assertThat(alive.get().getDeletedAt()).isNull();
    }

    @Test
    void findByIdAndDeletedAtIsNull_skipsSoftDeletedSession() {
        Optional<TrainingSession> deleted = repo.findByIdAndDeletedAtIsNull(softDeletedId);
        assertThat(deleted).isEmpty();
    }

    @Test
    void findFiltered_byStudentId_returnsOnlyThatStudentsSessions() {
        Page<TrainingSession> page = repo.findFiltered(
                STUDENT_ID.toString(), null, null, null, PageRequest.of(0, 100));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allMatch(s -> s.getStudentId().equals(STUDENT_ID));
        // Soft-deleted sessions must never leak into filtered results
        assertThat(page.getContent()).noneMatch(s -> s.getId().equals(softDeletedId));
    }

    @Test
    void findFiltered_byStatusInProgress_includesOurAliveSession() {
        Page<TrainingSession> page = repo.findFiltered(
                STUDENT_ID.toString(), "IN_PROGRESS", null, null, PageRequest.of(0, 100));
        assertThat(page.getContent()).extracting(TrainingSession::getId).contains(aliveId);
    }

    @Test
    void findFiltered_byStatusCompleted_excludesOurAliveInProgressSession() {
        Page<TrainingSession> page = repo.findFiltered(
                STUDENT_ID.toString(), "COMPLETED", null, null, PageRequest.of(0, 100));
        assertThat(page.getContent()).extracting(TrainingSession::getId).doesNotContain(aliveId);
    }

    @Test
    void findFiltered_withPagination_windowsTheResultSet() {
        Page<TrainingSession> p0 = repo.findFiltered(
                STUDENT_ID.toString(), null, null, null, PageRequest.of(0, 1));
        assertThat(p0.getContent()).hasSize(1);
        assertThat(p0.getSize()).isEqualTo(1);
    }

    private TrainingSession makeSession(UUID id, UUID studentId, String status, Instant deletedAt) {
        TrainingSession s = new TrainingSession();
        s.setId(id);
        s.setStudentId(studentId);
        s.setCourseId(COURSE_ID);
        s.setRestSecondsDefault(60);
        s.setStatus(status);
        s.setStartedAt(Instant.parse("2026-04-20T09:00:00Z"));
        s.setClientUpdatedAt(Instant.parse("2026-04-20T09:00:00Z"));
        s.setDeletedAt(deletedAt);
        return s;
    }
}
