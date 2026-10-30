package com.padmakar.trustflow.service;

import com.padmakar.trustflow.repository.OutboxEventRepository;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "trustflow.notifications.mode", havingValue = "kafka")
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishOne(Long id) throws Exception {
        var event = repository.lockById(id).orElseThrow();
        if (event.isPublished()) return;
        // A bounded send holds one row lock. A crash after send can still cause redelivery.
        kafka
            .send("trustflow-notifications", event.getEventId(), event.getPayload())
            .get(5, TimeUnit.SECONDS);
        event.setPublished(true);
    }
}
