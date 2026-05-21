package com.meridian.repository;

import com.meridian.entity.OperationalTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OperationalTransactionRepository extends JpaRepository<OperationalTransaction, UUID> {
}
