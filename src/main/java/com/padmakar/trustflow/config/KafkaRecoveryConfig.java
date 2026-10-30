package com.padmakar.trustflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "trustflow.notifications.mode", havingValue = "kafka")
public class KafkaRecoveryConfig {

    @Bean
    NewTopic notificationsTopic() {
        return TopicBuilder.name("trustflow-notifications").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic notificationsDeadLetterTopic() {
        return TopicBuilder.name("trustflow-notifications.DLT").partitions(1).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler notificationErrorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, error) ->
            new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        recoverer.setFailIfSendResultIsError(true);
        // Original delivery plus two retries; retain record if DLT publication fails.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
