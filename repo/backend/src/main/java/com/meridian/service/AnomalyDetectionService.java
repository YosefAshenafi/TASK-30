package com.meridian.service;

import com.meridian.entity.Anomaly;
import com.meridian.exception.AppException;
import com.meridian.repository.AnomalyRepository;
import com.meridian.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final DeviceFingerprintService deviceFingerprintService;
    private final AnomalyRepository anomalyRepository;
    private final NotificationService notificationService;
    private final AuditEventRepository auditEventRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${meridian.rate-limit.export-per-10min:20}")
    private int exportRateLimit;

    public AnomalyDetectionService(DeviceFingerprintService deviceFingerprintService,
                                   AnomalyRepository anomalyRepository,
                                   NotificationService notificationService,
                                   AuditEventRepository auditEventRepository,
                                   JdbcTemplate jdbcTemplate) {
        this.deviceFingerprintService = deviceFingerprintService;
        this.anomalyRepository = anomalyRepository;
        this.notificationService = notificationService;
        this.auditEventRepository = auditEventRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Checks whether the given fingerprint is a new device for the user.
     * If so, records an anomaly and notifies the user.
     */
    @Transactional
    public void checkNewDevice(UUID userId, String fingerprint, String ipAddress) {
        String result = deviceFingerprintService.checkAndRegister(userId, fingerprint);
        if ("NEW_DEVICE".equals(result)) {
            Anomaly anomaly = new Anomaly();
            anomaly.setUserId(userId);
            anomaly.setType("NEW_DEVICE");
            anomaly.setDetails("{\"message\":\"Login from a new device detected\"}");
            anomaly.setIpAddress(ipAddress);
            anomaly.setDeviceFingerprint(fingerprint);
            anomalyRepository.save(anomaly);
            notificationService.notify(userId, "anomaly_alert",
                    "A login from a new device was detected. If this was not you, please contact support.");
        }
    }

    /**
     * Checks whether the IP address falls within any allowed CIDR range loaded from
     * the security_policies table. Saves an anomaly and notifies if it does not match
     * and the policy mode is WARN.
     */
    @Transactional
    public void checkIpRange(UUID userId, String ipAddress) {
        try {
            String cidrRanges = jdbcTemplate.queryForObject(
                    "SELECT value FROM security_policies WHERE key = 'allowed_cidr_ranges'",
                    String.class);
            if (cidrRanges == null || cidrRanges.isBlank()) {
                return;
            }

            String mode = "WARN";
            try {
                String modeValue = jdbcTemplate.queryForObject(
                        "SELECT value FROM security_policies WHERE key = 'ip_enforcement_mode'",
                        String.class);
                if (modeValue != null) {
                    mode = modeValue.trim().toUpperCase();
                }
            } catch (Exception e) {
                log.debug("ip_enforcement_mode not found in security_policies, defaulting to WARN");
            }

            String[] ranges = cidrRanges.split(",");
            for (String cidr : ranges) {
                if (isInRange(ipAddress, cidr.trim())) {
                    return;
                }
            }

            if ("WARN".equals(mode)) {
                Anomaly anomaly = new Anomaly();
                anomaly.setUserId(userId);
                anomaly.setType("SUSPICIOUS_IP");
                anomaly.setDetails("{\"message\":\"Login from IP outside allowed ranges\",\"ip\":\"" + ipAddress + "\"}");
                anomaly.setIpAddress(ipAddress);
                anomalyRepository.save(anomaly);
                notificationService.notify(userId, "anomaly_alert",
                        "Login detected from an unrecognized IP address: " + ipAddress);
            }
        } catch (Exception e) {
            log.warn("IP range check failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Checks the user's export rate over the last 10 minutes. If it exceeds the
     * configured limit, saves an anomaly and notifies administrators.
     */
    @Transactional
    public void checkExportRate(UUID userId) {
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        long count = auditEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                userId, "EXPORT", tenMinutesAgo);

        if (count >= exportRateLimit) {
            Anomaly anomaly = new Anomaly();
            anomaly.setUserId(userId);
            anomaly.setType("EXPORT_RATE_EXCEEDED");
            anomaly.setDetails("{\"message\":\"Export rate exceeded\",\"count\":" + count + "}");
            anomalyRepository.save(anomaly);
            notificationService.notifyRole("ROLE_ADMINISTRATOR", "anomaly_alert",
                    "User " + userId + " has exceeded export rate limit with " + count + " exports in 10 minutes.");
        }
    }

    /**
     * Enforces the export rate limit for the given user. If the user has reached or
     * exceeded the configured export rate limit in the last 10 minutes, records an
     * anomaly, notifies administrators, and throws HTTP 429 TOO_MANY_REQUESTS.
     */
    // Intentionally NOT @Transactional: this method records the anomaly/notification and then
    // throws a 429. If the save and the throw shared one transaction, the thrown exception would
    // roll back the just-saved anomaly. Without an enclosing transaction (the caller is a
    // controller), each repository save commits in its own transaction before the throw.
    public void enforceExportRateLimit(UUID userId) {
        Instant tenMinutesAgo = Instant.now().minusSeconds(600);
        long count = auditEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(
                userId, "EXPORT", tenMinutesAgo);

        if (count >= exportRateLimit) {
            Anomaly anomaly = new Anomaly();
            anomaly.setUserId(userId);
            anomaly.setType("EXPORT_RATE_EXCEEDED");
            anomaly.setDetails("{\"message\":\"Export rate limit enforced\",\"count\":" + count + "}");
            anomalyRepository.save(anomaly);
            notificationService.notifyRole("ROLE_ADMINISTRATOR", "anomaly_alert",
                    "User " + userId + " has exceeded export rate limit with " + count + " exports in 10 minutes.");
            throw AppException.tooManyRequests(
                    "Export rate limit exceeded. Limit: " + exportRateLimit + " exports per 10 minutes.");
        }
    }

    /**
     * Determines whether an IP address falls within a given CIDR range.
     * Handles "0.0.0.0/0" as a match-all wildcard.
     *
     * @param ip   the IP address to test
     * @param cidr the CIDR notation range (e.g. "192.168.1.0/24")
     * @return true if the IP falls within the range, false otherwise
     */
    public boolean isInRange(String ip, String cidr) {
        try {
            if ("0.0.0.0/0".equals(cidr)) {
                return true;
            }
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1].trim());
            byte[] networkBytes = InetAddress.getByName(parts[0].trim()).getAddress();
            byte[] ipBytes = InetAddress.getByName(ip.trim()).getAddress();

            if (networkBytes.length != ipBytes.length) {
                return false;
            }

            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != ipBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < networkBytes.length) {
                int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                if ((networkBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to parse CIDR range '{}': {}", cidr, e.getMessage());
            return false;
        }
    }
}
