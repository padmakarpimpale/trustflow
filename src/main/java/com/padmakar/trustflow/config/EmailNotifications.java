package com.padmakar.trustflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padmakar.trustflow.repository.EmailDeliveryRepository;
import com.padmakar.trustflow.service.*;
import java.time.Instant;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trustflow.email.enabled", havingValue = "true")
public class EmailNotifications {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifications.class);
    private final ObjectMapper mapper;
    private final EmailQueue queue;
    private final EmailDeliveryRepository deliveries;
    private final EmailDispatcher dispatcher;

    public EmailNotifications(
        ObjectMapper mapper,
        EmailQueue queue,
        EmailDeliveryRepository deliveries,
        EmailDispatcher dispatcher
    ) {
        this.mapper = mapper;
        this.queue = queue;
        this.deliveries = deliveries;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
        topics = "trustflow-notifications",
        groupId = "trustflow-email",
        containerFactory = "emailKafkaListenerContainerFactory"
    )
    public void receive(String json) throws Exception {
        queue.enqueue(mapper.readValue(json, NotificationService.Notice.class));
    }

    @Scheduled(fixedDelayString = "${trustflow.email.poll-ms:5000}")
    public void dispatch() {
        for (var delivery : deliveries.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            "PENDING",
            Instant.now()
        )) {
            try {
                dispatcher.sendOne(delivery.getId());
            } catch (RuntimeException e) {
                log.warn(
                    "Email delivery {} remains pending ({})",
                    delivery.getId(),
                    e.getClass().getSimpleName()
                );
            }
        }
    }
}
