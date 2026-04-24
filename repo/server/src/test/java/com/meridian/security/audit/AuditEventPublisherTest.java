package com.meridian.security.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private AuditEventRepository repository;

    @InjectMocks
    private AuditEventPublisher publisher;

    @Test
    void publish_persistsEventViaRepository() {
        AuditEvent event = AuditEvent.of(UUID.randomUUID(), "LOGIN_SUCCESS",
                "USER", UUID.randomUUID().toString(), "{}");
        when(repository.save(event)).thenReturn(event);

        AuditEvent returned = publisher.publish(event);

        assertThat(returned).isSameAs(event);
        verify(repository).save(event);
    }

    @Test
    void publish_withNullActor_stillSaves() {
        AuditEvent event = AuditEvent.of(null, "SYSTEM_EVENT", "SYSTEM", "sys", "{}");
        when(repository.save(event)).thenReturn(event);

        AuditEvent returned = publisher.publish(event);

        assertThat(returned).isSameAs(event);
        assertThat(returned.getAction()).isEqualTo("SYSTEM_EVENT");
        verify(repository).save(event);
    }

    @Test
    void publish_preservesEventFields() {
        UUID actor = UUID.randomUUID();
        String targetId = UUID.randomUUID().toString();
        AuditEvent event = AuditEvent.of(actor, "PERMISSION_CHANGE", "USER", targetId,
                "{\"change\":\"role\"}");
        event.setIpAddress("10.0.0.1");
        when(repository.save(event)).thenReturn(event);

        publisher.publish(event);

        assertThat(event.getActorId()).isEqualTo(actor);
        assertThat(event.getAction()).isEqualTo("PERMISSION_CHANGE");
        assertThat(event.getTargetId()).isEqualTo(targetId);
        assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
        verify(repository).save(event);
    }
}
