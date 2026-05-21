package com.meridian.service;

import com.meridian.entity.DeviceFingerprint;
import com.meridian.repository.DeviceFingerprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceFingerprintService {

    private final DeviceFingerprintRepository deviceFingerprintRepository;

    public DeviceFingerprintService(DeviceFingerprintRepository deviceFingerprintRepository) {
        this.deviceFingerprintRepository = deviceFingerprintRepository;
    }

    /**
     * Computes a SHA-256 hex fingerprint from browser/device signals.
     */
    public String computeFingerprint(String userAgent, String acceptLang, String tzOffset) {
        String raw = userAgent + "|" + acceptLang + "|" + tzOffset;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Checks whether the fingerprint is known for the user.
     * If new, persists it and returns "NEW_DEVICE".
     * If known, updates lastSeen and returns "KNOWN_DEVICE".
     */
    @Transactional
    public String checkAndRegister(UUID userId, String fingerprint) {
        Optional<DeviceFingerprint> existing = deviceFingerprintRepository
                .findByUserIdAndFingerprintHash(userId, fingerprint);

        if (existing.isEmpty()) {
            DeviceFingerprint fp = new DeviceFingerprint();
            fp.setUserId(userId);
            fp.setFingerprintHash(fingerprint);
            fp.setLastSeen(Instant.now());
            deviceFingerprintRepository.save(fp);
            return "NEW_DEVICE";
        }

        DeviceFingerprint fp = existing.get();
        fp.setLastSeen(Instant.now());
        deviceFingerprintRepository.save(fp);
        return "KNOWN_DEVICE";
    }
}
