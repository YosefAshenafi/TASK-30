package com.meridian.repository;

import com.meridian.entity.ReportSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    Page<ReportSchedule> findByUserId(UUID userId, Pageable pageable);

    Page<ReportSchedule> findByOrganizationId(UUID orgId, Pageable pageable);
}
