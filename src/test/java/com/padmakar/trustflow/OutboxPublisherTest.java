package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.padmakar.trustflow.model.OutboxEvent;
import com.padmakar.trustflow.repository.OutboxEventRepository;
import com.padmakar.trustflow.service.OutboxPublisher;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OutboxPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void failedPublicationStaysPendingAndCanRetry() throws Exception {
        var repo = mock(OutboxEventRepository.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var event = new OutboxEvent();
        event.setEventId("event-1");
        event.setPayload("{}");
        when(repo.lockById(1L)).thenReturn(Optional.of(event));
        when(kafka.send("trustflow-notifications", "event-1", "{}"))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")))
            .thenReturn(CompletableFuture.completedFuture(null));
        var publisher = new OutboxPublisher(repo, kafka);
        assertThrows(Exception.class, () -> publisher.publishOne(1L));
        assertFalse(event.isPublished());
        publisher.publishOne(1L);
        assertTrue(event.isPublished());
        publisher.publishOne(1L);
        verify(kafka, times(2)).send("trustflow-notifications", "event-1", "{}");
    }
}
