package com.padmakar.trustflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padmakar.trustflow.repository.OutboxEventRepository;
import com.padmakar.trustflow.service.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trustflow.notifications.mode", havingValue = "kafka")
public class KafkaNotifications {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotifications.class);
    private final OutboxEventRepository outbox;
    private final OutboxPublisher publisher;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public KafkaNotifications(
        OutboxEventRepository outbox,
        OutboxPublisher publisher,
        NotificationService notifications,
        ObjectMapper mapper
    ) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelayString = "${trustflow.outbox.delay-ms:5000}")
    public void publish() {
        for (var event : outbox.findTop20ByPublishedFalseOrderByIdAsc()) {
            try {
                publisher.publishOne(event.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn(
                    "Notification event {} remains pending ({})",
                    event.getEventId(),
                    e.getClass().getSimpleName()
                );
                return;
            }
        }
    }

    @KafkaListener(topics = "trustflow-notifications")
    public void receive(String json) throws Exception {
        notifications.accept(mapper.readValue(json, NotificationService.Notice.class));
    }
}
