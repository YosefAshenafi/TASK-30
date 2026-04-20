package com.meridian.sessions;

import com.meridian.common.idempotency.IdempotencyService;
import com.meridian.sessions.dto.SyncRequest;
import com.meridian.sessions.dto.SyncResult;
import com.meridian.sessions.entity.SessionActivitySet;
import com.meridian.sessions.entity.TrainingSession;
import com.meridian.sessions.repository.SessionActivitySetRepository;
import com.meridian.sessions.repository.TrainingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies LWW (last-write-wins) and idempotency-key conflict handling in SyncResolver.
 *
 * LWW rule:
 *   - newer clientUpdatedAt → UPDATED
 *   - older clientUpdatedAt → NOOP (OLDER_CLIENT_TIMESTAMP conflict)
 *   - same idem key + same body → NOOP
 *   - same idem key + different body → IDEMPOTENCY_MISMATCH conflict
 */
class SyncResolverTest {

    private TrainingSessionRepository sessionRepo;
    private SessionActivitySetRepository setRepo;
    private IdempotencyService idempotencyService;
    private SyncResolver resolver;

    @BeforeEach
    void setUp() {
        sessionRepo = Mockito.mock(TrainingSessionRepository.class);
        setRepo = Mockito.mock(SessionActivitySetRepository.class);
        idempotencyService = Mockito.mock(IdempotencyService.class);
        resolver = new SyncResolver(sessionRepo, setRepo, idempotencyService);
    }

    @Test
    void newSessionIsCreated() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SessionSyncItem item = buildSessionItem(id, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("hash1");
        when(idempotencyService.check(anyString(), eq("hash1"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("CREATED");
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void existingSessionWithNewerClientTimestampIsUpdated() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant serverTs = Instant.parse("2026-04-01T12:00:00Z");
        Instant clientTs = Instant.parse("2026-04-01T13:00:00Z"); // newer

        TrainingSession existing = new TrainingSession();
        existing.setId(id);
        existing.setStudentId(userId);
        existing.setClientUpdatedAt(serverTs);
        existing.setStatus("IN_PROGRESS");

        SyncRequest.SessionSyncItem item = buildSessionItem(id, clientTs);

        when(idempotencyService.hashBody(anyString())).thenReturn("hash2");
        when(idempotencyService.check(anyString(), eq("hash2"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("UPDATED");
    }

    @Test
    void existingSessionWithOlderClientTimestampBecomesConflict() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant serverTs = Instant.parse("2026-04-01T14:00:00Z");
        Instant clientTs = Instant.parse("2026-04-01T12:00:00Z"); // older

        TrainingSession existing = new TrainingSession();
        existing.setId(id);
        existing.setStudentId(userId);
        existing.setClientUpdatedAt(serverTs);
        existing.setStatus("COMPLETED");

        SyncRequest.SessionSyncItem item = buildSessionItem(id, clientTs);

        when(idempotencyService.hashBody(anyString())).thenReturn("hash3");
        when(idempotencyService.check(anyString(), eq("hash3"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("OLDER_CLIENT_TIMESTAMP");
    }

    @Test
    void idempotencyMismatchBecomesConflict() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SessionSyncItem item = buildSessionItem(id, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("hash4");
        when(idempotencyService.check(anyString(), eq("hash4"), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_MISMATCH"));
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("IDEMPOTENCY_MISMATCH");
    }

    @Test
    void cachedIdempotencyKeyReturnsCachedResponse() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SessionSyncItem item = buildSessionItem(id, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("hash5");
        when(idempotencyService.check(anyString(), eq("hash5"), any()))
            .thenReturn(Optional.of(Mockito.mock(com.meridian.sessions.dto.TrainingSessionDto.class)));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("NOOP");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void newSetIsCreated() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        TrainingSession ownerSession = new TrainingSession();
        ownerSession.setId(sessionId);
        ownerSession.setStudentId(userId);

        when(idempotencyService.hashBody(anyString())).thenReturn("set-hash1");
        when(idempotencyService.check(anyString(), eq("set-hash1"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(sessionId)).thenReturn(Optional.of(ownerSession));
        when(setRepo.findBySessionIdAndActivityIdAndSetIndex(any(), any(), anyInt())).thenReturn(Optional.empty());
        when(setRepo.save(any())).thenAnswer(inv -> {
            SessionActivitySet s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("CREATED");
        assertThat(result.conflicts()).isEmpty();
    }

    @Test
    void existingSetWithNewerTimestampIsUpdated() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant serverTs = Instant.parse("2026-04-01T12:00:00Z");
        Instant clientTs = Instant.parse("2026-04-01T13:00:00Z");

        SessionActivitySet existing = new SessionActivitySet();
        existing.setId(UUID.randomUUID());
        existing.setSessionId(sessionId);
        existing.setActivityId(UUID.randomUUID());
        existing.setSetIndex(1);
        existing.setClientUpdatedAt(serverTs);

        TrainingSession ownerSession = new TrainingSession();
        ownerSession.setId(sessionId);
        ownerSession.setStudentId(userId);

        SyncRequest.SetSyncItem item = buildSetItem(sessionId, clientTs);

        when(idempotencyService.hashBody(anyString())).thenReturn("set-hash2");
        when(idempotencyService.check(anyString(), eq("set-hash2"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(sessionId)).thenReturn(Optional.of(ownerSession));
        when(setRepo.findBySessionIdAndActivityIdAndSetIndex(any(), any(), anyInt()))
                .thenReturn(Optional.of(existing));
        when(setRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("UPDATED");
    }

    @Test
    void existingSetWithOlderTimestampBecomesConflict() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant serverTs = Instant.parse("2026-04-01T14:00:00Z");
        Instant clientTs = Instant.parse("2026-04-01T12:00:00Z");

        SessionActivitySet existing = new SessionActivitySet();
        existing.setId(UUID.randomUUID());
        existing.setSessionId(sessionId);
        existing.setClientUpdatedAt(serverTs);

        TrainingSession ownerSession = new TrainingSession();
        ownerSession.setId(sessionId);
        ownerSession.setStudentId(userId);

        SyncRequest.SetSyncItem item = buildSetItem(sessionId, clientTs);

        when(idempotencyService.hashBody(anyString())).thenReturn("set-hash3");
        when(idempotencyService.check(anyString(), eq("set-hash3"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(sessionId)).thenReturn(Optional.of(ownerSession));
        when(setRepo.findBySessionIdAndActivityIdAndSetIndex(any(), any(), anyInt()))
                .thenReturn(Optional.of(existing));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("OLDER_CLIENT_TIMESTAMP");
    }

    @Test
    void setIdempotencyMismatchBecomesConflict() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("set-hash4");
        when(idempotencyService.check(anyString(), eq("set-hash4"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_MISMATCH"));
        when(setRepo.findBySessionIdAndActivityIdAndSetIndex(any(), any(), anyInt())).thenReturn(Optional.empty());
        // idempotency mismatch is caught before the ownership check, so no sessionRepo mock needed

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("IDEMPOTENCY_MISMATCH");
    }

    @Test
    void cachedSetReturnsNoop() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("set-hash5");
        when(idempotencyService.check(anyString(), eq("set-hash5"), any()))
                .thenReturn(Optional.of(Mockito.mock(com.meridian.sessions.dto.SessionSetDto.class)));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("NOOP");
        verify(setRepo, never()).save(any());
    }

    @Test
    void createSessionUsesDefaultsForNullStatusAndStartedAt() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SessionSyncItem item = new SyncRequest.SessionSyncItem(
            UUID.randomUUID().toString(), id, UUID.randomUUID(), null, 60, null, null, null, Instant.now()
        );

        when(idempotencyService.hashBody(anyString())).thenReturn("hash-null");
        when(idempotencyService.check(anyString(), eq("hash-null"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("CREATED");
    }

    @Test
    void existingSessionForDifferentUserIsAccessDenied() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        Instant ts = Instant.now();

        TrainingSession existing = new TrainingSession();
        existing.setId(id);
        existing.setStudentId(otherUser);
        existing.setClientUpdatedAt(ts.minusSeconds(60));
        existing.setStatus("IN_PROGRESS");

        SyncRequest.SessionSyncItem item = buildSessionItem(id, ts);

        when(idempotencyService.hashBody(anyString())).thenReturn("hash-ad");
        when(idempotencyService.check(anyString(), eq("hash-ad"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("ACCESS_DENIED");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void setSyncWhenSessionMissing_isAccessDenied() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("set-ad1");
        when(idempotencyService.check(anyString(), eq("set-ad1"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(sessionId)).thenReturn(Optional.empty());

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void setSyncWhenSessionBelongsToAnotherUser_isAccessDenied() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        TrainingSession ownerSession = new TrainingSession();
        ownerSession.setId(sessionId);
        ownerSession.setStudentId(otherUser);

        when(idempotencyService.hashBody(anyString())).thenReturn("set-ad2");
        when(idempotencyService.check(anyString(), eq("set-ad2"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(sessionId)).thenReturn(Optional.of(ownerSession));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId);

        assertThat(result.conflicts()).hasSize(1);
        assertThat(result.conflicts().get(0).reason()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void idempotencyNonConflictRethrows() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SessionSyncItem item = buildSessionItem(id, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("hash-re");
        when(idempotencyService.check(anyString(), eq("hash-re"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTHER"));

        assertThrows(ResponseStatusException.class,
                () -> resolver.resolve(new SyncRequest(List.of(item), List.of()), userId));
    }

    @Test
    void setIdempotencyNonConflictRethrows() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SyncRequest.SetSyncItem item = buildSetItem(sessionId, Instant.now());

        when(idempotencyService.hashBody(anyString())).thenReturn("set-re");
        when(idempotencyService.check(anyString(), eq("set-re"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTHER"));

        assertThrows(ResponseStatusException.class,
                () -> resolver.resolve(new SyncRequest(List.of(), List.of(item)), userId));
    }

    @Test
    void applySessionUpdateSkipsNullStatus() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant serverTs = Instant.parse("2026-04-01T10:00:00Z");
        Instant clientTs = Instant.parse("2026-04-01T11:00:00Z");

        TrainingSession existing = new TrainingSession();
        existing.setId(id);
        existing.setStudentId(userId);
        existing.setClientUpdatedAt(serverTs);
        existing.setStatus("IN_PROGRESS");

        SyncRequest.SessionSyncItem item = new SyncRequest.SessionSyncItem(
            UUID.randomUUID().toString(), id, UUID.randomUUID(), null, 60, null, null, null, clientTs
        );

        when(idempotencyService.hashBody(anyString())).thenReturn("hash-null2");
        when(idempotencyService.check(anyString(), eq("hash-null2"), any())).thenReturn(Optional.empty());
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(existing));
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = resolver.resolve(new SyncRequest(List.of(item), List.of()), userId);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).status()).isEqualTo("UPDATED");
        assertThat(existing.getStatus()).isEqualTo("IN_PROGRESS");
    }

    private SyncRequest.SessionSyncItem buildSessionItem(UUID id, Instant clientTs) {
        return new SyncRequest.SessionSyncItem(
            UUID.randomUUID().toString(), id, UUID.randomUUID(), null, 60, "IN_PROGRESS", null, null, clientTs
        );
    }

    private SyncRequest.SetSyncItem buildSetItem(UUID sessionId, Instant clientTs) {
        return new SyncRequest.SetSyncItem(
            UUID.randomUUID().toString(), sessionId, UUID.randomUUID(), 1, 60, null, null, clientTs
        );
    }
}
