package com.padmakar.trustflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padmakar.trustflow.repository.OutboxEventRepository;
import com.padmakar.trustflow.service.NotificationService;
import java.util.concurrent.TimeUnit;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trustflow.notifications.mode", havingValue = "kafka")
public class KafkaNotifications {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotifications.class);
    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public KafkaNotifications(
        OutboxEventRepository outbox,
        KafkaTemplate<String, String> kafka,
        NotificationService notifications,
        ObjectMapper mapper
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void publish() {
        for (var event : outbox.findTop20ByPublishedFalseOrderByIdAsc()) {
            try {
                kafka
                    .send("trustflow-notifications", event.getEventId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
                event.setPublished(true);
                outbox.save(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Notification event {} will be retried", event.getEventId());
                return;
            }
        }
    }

    @KafkaListener(topics = "trustflow-notifications")
    public void receive(String json) throws Exception {
        notifications.accept(mapper.readValue(json, NotificationService.Notice.class));
    }
}
