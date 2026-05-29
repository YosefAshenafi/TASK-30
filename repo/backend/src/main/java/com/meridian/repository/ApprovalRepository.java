package com.meridian.repository;

import com.meridian.entity.Approval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    Page<Approval> findByApproverIdAndStatus(UUID approverId, String status, Pageable pageable);

    Page<Approval> findByRequesterId(UUID requesterId, Pageable pageable);

    Optional<Approval> findByIdAndStatus(UUID id, String status);

    long countByStatus(String status);
}
