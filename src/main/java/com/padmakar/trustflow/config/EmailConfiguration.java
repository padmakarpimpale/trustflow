package com.padmakar.trustflow.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.Assert;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "trustflow.email.enabled", havingValue = "true")
@EnableConfigurationProperties(EmailSettings.class)
public class EmailConfiguration {

    public EmailConfiguration(@Value("${trustflow.notifications.mode}") String mode) {
        Assert.state(
            "kafka".equals(mode),
            "Email requires Kafka notification mode (integrated profile)"
        );
    }

    @Bean
    NewTopic emailDeadLetterTopic() {
        return TopicBuilder.name("trustflow-notifications.email.DLT")
            .partitions(1)
            .replicas(1)
            .build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> emailKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
        ConsumerFactory<Object, Object> consumerFactory,
        KafkaTemplate<String, String> template
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, error) ->
            new TopicPartition("trustflow-notifications.email.DLT", record.partition())
        );
        recoverer.setFailIfSendResultIsError(true);
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L))
        );
        return factory;
    }
}
