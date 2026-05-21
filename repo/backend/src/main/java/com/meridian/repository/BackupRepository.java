package com.meridian.repository;

import com.meridian.entity.Backup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BackupRepository extends JpaRepository<Backup, UUID> {

    Page<Backup> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Backup> findByRetentionUntilBefore(Instant cutoff);
}
