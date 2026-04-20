package com.meridian.auth;

import com.meridian.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class LockoutPolicyTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
    }

    @Test
    void notLockedWhenStatusIsActive() {
        assertThat(LockoutPolicy.isLockedOut(user)).isFalse();
    }

    @Test
    void lockedWhenStatusLockedAndFuture() {
        user.setStatus("LOCKED");
        user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        assertThat(LockoutPolicy.isLockedOut(user)).isTrue();
    }

    @Test
    void notLockedWhenLockExpired() {
        user.setStatus("LOCKED");
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(LockoutPolicy.isLockedOut(user)).isFalse();
    }

    @Test
    void recordFailureIncrementsCount() {
        LockoutPolicy.recordFailure(user);
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void fifthFailureLocksAccount() {
        for (int i = 0; i < LockoutPolicy.MAX_FAILURES; i++) {
            LockoutPolicy.recordFailure(user);
        }
        assertThat(user.getStatus()).isEqualTo("LOCKED");
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void lockoutDurationIsConfigured() {
        for (int i = 0; i < LockoutPolicy.MAX_FAILURES; i++) {
            LockoutPolicy.recordFailure(user);
        }
        Instant expected = Instant.now().plus(LockoutPolicy.LOCKOUT_MINUTES - 1, ChronoUnit.MINUTES);
        assertThat(user.getLockedUntil()).isAfter(expected);
    }

    @Test
    void resetOnSuccessClearsCounter() {
        user.setFailedLoginCount(3);
        LockoutPolicy.resetOnSuccess(user);
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void clearExpiredLockRestoresActiveStatus() {
        user.setStatus("LOCKED");
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        user.setFailedLoginCount(5);
        LockoutPolicy.clearExpiredLock(user);
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getFailedLoginCount()).isZero();
    }

    @Test
    void clearExpiredLockNoopWhenStillLocked() {
        user.setStatus("LOCKED");
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        LockoutPolicy.clearExpiredLock(user);
        assertThat(user.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void fourthFailureDoesNotLock() {
        for (int i = 0; i < LockoutPolicy.MAX_FAILURES - 1; i++) {
            LockoutPolicy.recordFailure(user);
        }
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }
}
