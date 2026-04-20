package com.meridian.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.sessions.entity.IdempotencyKey;
import com.meridian.sessions.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private IdempotencyKeyRepository repository;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(IdempotencyKeyRepository.class);
        service = new IdempotencyService(repository, new ObjectMapper());
    }

    @Test
    void hashBodyIsConsistent() {
        String body = "{\"key\":\"value\"}";
        String h1 = service.hashBody(body);
        String h2 = service.hashBody(body);
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64); // SHA-256 hex
    }

    @Test
    void hashBodyDiffersForDifferentBodies() {
        assertThat(service.hashBody("body1")).isNotEqualTo(service.hashBody("body2"));
    }

    @Test
    void checkReturnsCachedValueOnHit() throws Exception {
        String key = "idem-key-1";
        String hash = service.hashBody("{\"a\":1}");
        IdempotencyKey cached = buildKey(key, hash, "42");
        when(repository.findById(key)).thenReturn(Optional.of(cached));

        Optional<Integer> result = service.check(key, hash, Integer.class);
        assertThat(result).contains(42);
    }

    @Test
    void checkThrowsConflictOnHashMismatch() {
        String key = "idem-key-2";
        IdempotencyKey cached = buildKey(key, "old-hash", "{\"value\":1}");
        when(repository.findById(key)).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.check(key, "new-hash", Integer.class))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("IDEMPOTENCY_MISMATCH");
    }

    @Test
    void checkReturnEmptyWhenKeyIsNull() {
        Optional<Integer> result = service.check(null, "hash", Integer.class);
        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void checkReturnEmptyWhenKeyNotFound() {
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        assertThat(service.check("missing", "hash", String.class)).isEmpty();
    }

    @Test
    void storeNoopWhenKeyIsNull() {
        service.store(null, UUID.randomUUID(), "hash", "data");
        verifyNoInteractions(repository);
    }

    @Test
    void checkReturnsEmptyWhenDeserializationFails() {
        String key = "idem-key-fail";
        String hash = service.hashBody("{}");
        IdempotencyKey cached = buildKey(key, hash, "not-valid-json-for-integer");
        when(repository.findById(key)).thenReturn(Optional.of(cached));

        Optional<Integer> result = service.check(key, hash, Integer.class);
        assertThat(result).isEmpty();
    }

    @Test
    void storeWithNonNullKey() {
        String key = "store-key-1";
        UUID userId = UUID.randomUUID();
        String hash = "h1";

        service.store(key, userId, hash, 99);

        verify(repository).save(argThat(ik ->
            ik.getKey().equals(key) &&
            ik.getUserId().equals(userId) &&
            ik.getRequestHash().equals(hash)));
    }

    private IdempotencyKey buildKey(String key, String hash, String json) {
        IdempotencyKey k = new IdempotencyKey();
        k.setKey(key);
        k.setUserId(UUID.randomUUID());
        k.setRequestHash(hash);
        k.setResponseJson(json);
        k.setCreatedAt(Instant.now());
        return k;
    }
}
