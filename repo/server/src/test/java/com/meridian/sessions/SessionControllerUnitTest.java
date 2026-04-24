package com.meridian.sessions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.common.idempotency.IdempotencyService;
import com.meridian.common.security.AuthPrincipal;
import com.meridian.sessions.dto.CreateSessionRequest;
import com.meridian.sessions.dto.PatchSessionRequest;
import com.meridian.sessions.dto.TrainingSessionDto;
import com.meridian.sessions.entity.TrainingSession;
import com.meridian.sessions.repository.SessionActivitySetRepository;
import com.meridian.sessions.repository.TrainingSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit test for {@link SessionController}.
 *
 * Mocks repositories/services and invokes the controller methods directly to
 * pin behavior that does not surface cleanly in end-to-end API tests:
 *  - owner-check short-circuit on create + patch
 *  - pause/continue state-machine enforcement (409 vs 200)
 *  - cross-student access denial
 *  - idempotency-key cache hit path
 *
 * This lives alongside the API and TrueNoMockHttp suites, providing a third,
 * fast feedback loop that does not need Spring context to boot.
 */
class SessionControllerUnitTest {

    private TrainingSessionRepository sessionRepo;
    private SessionActivitySetRepository setRepo;
    private IdempotencyService idempotencyService;
    private ObjectMapper objectMapper;
    private UserRepository userRepository;
    private SessionController controller;

    private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-00000000cc10");

    @BeforeEach
    void setUp() {
        sessionRepo = mock(TrainingSessionRepository.class);
        setRepo = mock(SessionActivitySetRepository.class);
        idempotencyService = mock(IdempotencyService.class);
        objectMapper = new ObjectMapper();
        userRepository = mock(UserRepository.class);
        controller = new SessionController(sessionRepo, setRepo, idempotencyService, objectMapper, userRepository);
    }

    @Test
    void create_happyPath_persistsSessionWithCallerAsStudent() {
        UUID id = UUID.randomUUID();
        CreateSessionRequest req = new CreateSessionRequest(id, COURSE_ID, null, 60,
                Instant.parse("2026-04-20T09:00:00Z"), Instant.parse("2026-04-20T09:00:00Z"));
        Authentication auth = studentAuth();
        HttpServletRequest httpReq = new MockHttpServletRequest();
        when(sessionRepo.existsById(id)).thenReturn(false);
        when(sessionRepo.save(any(TrainingSession.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<TrainingSessionDto> res = controller.create(req, auth, httpReq);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody().studentId()).isEqualTo(STUDENT_ID);
        assertThat(res.getBody().courseId()).isEqualTo(COURSE_ID);
        assertThat(res.getBody().restSecondsDefault()).isEqualTo(60);
        verify(sessionRepo).save(any(TrainingSession.class));
    }

    @Test
    void create_duplicateId_throwsConflict() {
        UUID id = UUID.randomUUID();
        CreateSessionRequest req = new CreateSessionRequest(id, COURSE_ID, null, 60,
                Instant.parse("2026-04-20T09:00:00Z"), Instant.parse("2026-04-20T09:00:00Z"));
        Authentication auth = studentAuth();
        HttpServletRequest httpReq = new MockHttpServletRequest();
        when(sessionRepo.existsById(id)).thenReturn(true);

        assertThatThrownBy(() -> controller.create(req, auth, httpReq))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("already exists");
        verify(sessionRepo, never()).save(any(TrainingSession.class));
    }

    @Test
    void create_withIdempotencyKeyCacheHit_returnsCachedDtoWithoutDoubleSave() {
        UUID id = UUID.randomUUID();
        CreateSessionRequest req = new CreateSessionRequest(id, COURSE_ID, null, 60,
                Instant.parse("2026-04-20T09:00:00Z"), Instant.parse("2026-04-20T09:00:00Z"));
        Authentication auth = studentAuth();
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setAttribute("idempotencyKey", "idem-1");
        TrainingSessionDto cached = new TrainingSessionDto(id, STUDENT_ID, COURSE_ID, null, 60,
                "IN_PROGRESS", Instant.parse("2026-04-20T09:00:00Z"), null,
                Instant.parse("2026-04-20T09:00:00Z"));
        when(idempotencyService.hashBody(any())).thenReturn("hash-1");
        when(idempotencyService.check(eq("idem-1"), eq(STUDENT_ID), eq("hash-1"), eq(TrainingSessionDto.class)))
                .thenReturn(Optional.of(cached));

        ResponseEntity<TrainingSessionDto> res = controller.create(req, auth, httpReq);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody()).isEqualTo(cached);
        verify(sessionRepo, never()).save(any(TrainingSession.class));
    }

    @Test
    void patch_foreignStudentSession_returns403() {
        UUID id = UUID.randomUUID();
        TrainingSession session = new TrainingSession();
        session.setId(id);
        session.setStudentId(OTHER_STUDENT_ID);
        session.setStatus("IN_PROGRESS");
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(session));
        PatchSessionRequest req = new PatchSessionRequest("PAUSED", null, null, Instant.now());

        assertThatThrownBy(() -> controller.patch(id, req, studentAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("Access denied");
    }

    @Test
    void pause_onAlreadyPausedSession_throws409() {
        UUID id = UUID.randomUUID();
        TrainingSession session = new TrainingSession();
        session.setId(id);
        session.setStudentId(STUDENT_ID);
        session.setStatus("PAUSED");
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> controller.pause(id, studentAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void continueSession_onNotPausedSession_throws409() {
        UUID id = UUID.randomUUID();
        TrainingSession session = new TrainingSession();
        session.setId(id);
        session.setStudentId(STUDENT_ID);
        session.setStatus("IN_PROGRESS");
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> controller.continueSession(id, studentAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void complete_setsCompletedAndPersists() {
        UUID id = UUID.randomUUID();
        TrainingSession session = new TrainingSession();
        session.setId(id);
        session.setStudentId(STUDENT_ID);
        session.setStatus("IN_PROGRESS");
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(session));
        when(sessionRepo.save(any(TrainingSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<TrainingSessionDto> res = controller.complete(id, studentAuth());

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().status()).isEqualTo("COMPLETED");
        assertThat(res.getBody().endedAt()).isNotNull();
    }

    @Test
    void getById_foreignStudent_throws403() {
        UUID id = UUID.randomUUID();
        TrainingSession session = new TrainingSession();
        session.setId(id);
        session.setStudentId(OTHER_STUDENT_ID);
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> controller.getById(id, studentAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void getById_nonExistentSession_throws404() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getById(id, studentAuth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void list_asCorporateMentorWithoutOrg_returns403() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "CORPORATE_MENTOR", null);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_CORPORATE_MENTOR")));

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, 0, 50, auth))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("organization scope");
    }

    @Test
    void list_asCorporateMentorWithForeignStudent_usesOrgFilterBranch() {
        // We don't care about the query result — just that the org-filter
        // branch is taken (verified by which repo method is called).
        UUID orgId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "CORPORATE_MENTOR", orgId);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_CORPORATE_MENTOR")));
        when(sessionRepo.findFilteredByOrg(any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        controller.list(null, null, null, null, null, 0, 50, auth);

        verify(sessionRepo).findFilteredByOrg(any(), any(), any(), any(), any(), any());
        verify(sessionRepo, never()).findFiltered(any(), any(), any(), any(), any());
    }

    // ─────────── helpers ───────────

    private Authentication studentAuth() {
        AuthPrincipal principal = new AuthPrincipal(STUDENT_ID, "STUDENT", null);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    @SuppressWarnings("unused")
    private User userWithOrg(UUID id, UUID orgId) {
        User u = new User();
        u.setId(id);
        u.setOrganizationId(orgId);
        return u;
    }
}
