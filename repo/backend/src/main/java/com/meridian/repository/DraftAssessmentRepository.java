package com.meridian.repository;

import com.meridian.entity.DraftAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DraftAssessmentRepository extends JpaRepository<DraftAssessment, UUID> {

    Optional<DraftAssessment> findByIdempotencyKey(String idempotencyKey);
}
