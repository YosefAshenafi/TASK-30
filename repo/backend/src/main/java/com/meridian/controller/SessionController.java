package com.meridian.controller;

import com.meridian.dto.SessionDto.CreateSessionRequest;
import com.meridian.dto.SessionDto.SessionActivityDto;
import com.meridian.dto.SessionDto.SessionResponse;
import com.meridian.dto.SessionDto.UpdateSessionRequest;
import com.meridian.entity.SessionActivity;
import com.meridian.entity.TrainingSession;
import com.meridian.repository.SessionActivityRepository;
import com.meridian.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@PreAuthorize("isAuthenticated()")
public class SessionController {

    private final SessionService sessionService;
    private final SessionActivityRepository sessionActivityRepository;

    public SessionController(SessionService sessionService,
                            SessionActivityRepository sessionActivityRepository) {
        this.sessionService = sessionService;
        this.sessionActivityRepository = sessionActivityRepository;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        TrainingSession session = sessionService.createSession(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    @GetMapping
    public ResponseEntity<Page<SessionResponse>> getSessions(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        Page<SessionResponse> page = sessionService.getSessionsByUser(user.getId(), pageable)
                .map(this::toDto);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        TrainingSession session = sessionService.getSessionById(id, user.getId());
        return ResponseEntity.ok(toDto(session));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SessionResponse> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        TrainingSession session = sessionService.updateSession(id, user.getId(), request);
        return ResponseEntity.ok(toDto(session));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> completeSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        TrainingSession session = sessionService.completeSession(id, user.getId());
        return ResponseEntity.ok(toDto(session));
    }

    @GetMapping("/{id}/activities")
    public ResponseEntity<List<SessionActivity>> getActivities(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        com.meridian.entity.User user = (com.meridian.entity.User) principal;
        List<SessionActivity> activities = sessionService.getActivities(id, user.getId());
        return ResponseEntity.ok(activities);
    }

    private SessionResponse toDto(TrainingSession session) {
        List<SessionActivityDto> activities = sessionActivityRepository
                .findBySessionId(session.getId())
                .stream()
                .map(a -> new SessionActivityDto(a.getId(), a.getActivityRef(), a.isCompleted()))
                .toList();
        return new SessionResponse(
                session.getId(),
                session.getUserId(),
                session.getCourseId(),
                session.getStatus().name(),
                session.getRestTimerSecs(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getSyncStatus().name(),
                activities
        );
    }
}
