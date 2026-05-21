package com.meridian.repository;

import com.meridian.entity.SessionActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionActivityRepository extends JpaRepository<SessionActivity, UUID> {

    List<SessionActivity> findBySessionId(UUID sessionId);
}
