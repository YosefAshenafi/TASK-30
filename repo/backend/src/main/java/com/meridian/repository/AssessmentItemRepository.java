package com.meridian.repository;

import com.meridian.entity.AssessmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssessmentItemRepository extends JpaRepository<AssessmentItem, UUID> {

    List<AssessmentItem> findByCourseId(UUID courseId);
}
