package com.meridian.repository;

import com.meridian.entity.TrainingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<TrainingSession, UUID> {

    Page<TrainingSession> findByUserId(UUID userId, Pageable pageable);

    Optional<TrainingSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<TrainingSession> findByIdempotencyKey(String key);

    boolean existsByIdempotencyKey(String key);

    long countByStatus(TrainingSession.SessionStatus status);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, TrainingSession.SessionStatus status);
}
