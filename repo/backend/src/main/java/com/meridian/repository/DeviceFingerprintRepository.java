package com.meridian.repository;

import com.meridian.entity.DeviceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceFingerprintRepository extends JpaRepository<DeviceFingerprint, UUID> {

    Optional<DeviceFingerprint> findByUserIdAndFingerprintHash(UUID userId, String hash);

    List<DeviceFingerprint> findByUserId(UUID userId);
}
