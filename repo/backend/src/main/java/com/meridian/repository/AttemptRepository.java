package com.meridian.repository;

import com.meridian.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    List<Attempt> findByUserId(UUID userId);

    List<Attempt> findByAssessmentItemId(UUID itemId);
}
