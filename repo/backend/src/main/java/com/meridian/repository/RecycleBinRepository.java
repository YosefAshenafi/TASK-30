package com.meridian.repository;

import com.meridian.entity.RecycleBinEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecycleBinRepository extends JpaRepository<RecycleBinEntry, UUID> {

    Page<RecycleBinEntry> findAll(Pageable pageable);

    List<RecycleBinEntry> findByExpiresAtBefore(Instant cutoff);

    Page<RecycleBinEntry> findByEntityType(String entityType, Pageable pageable);
}
