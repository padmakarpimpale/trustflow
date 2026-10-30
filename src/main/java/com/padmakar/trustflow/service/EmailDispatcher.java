package com.padmakar.trustflow.service;

import com.padmakar.trustflow.config.EmailSettings;
import com.padmakar.trustflow.repository.EmailDeliveryRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "trustflow.email.enabled", havingValue = "true")
public class EmailDispatcher {

    private final EmailDeliveryRepository deliveries;
    private final JavaMailSender mail;
    private final EmailSettings settings;

    public EmailDispatcher(
        EmailDeliveryRepository deliveries,
        JavaMailSender mail,
        EmailSettings settings
    ) {
        this.deliveries = deliveries;
        this.mail = mail;
        this.settings = settings;
    }

    @Transactional
    public void sendOne(Long id) {
        var delivery = deliveries.lockById(id).orElseThrow();
        if (
            !"PENDING".equals(delivery.getStatus()) ||
            delivery.getNextAttemptAt().isAfter(Instant.now())
        ) return;
        delivery.beginAttempt();
        var message = new SimpleMailMessage();
        message.setFrom(settings.getFrom());
        message.setTo(delivery.getRecipient());
        message.setSubject(delivery.getSubject());
        message.setText(delivery.getBody());
        try {
            mail.send(message);
            // SENT means SMTP accepted the message, not that the recipient read it.
            delivery.sent();
        } catch (MailException e) {
            // Store only the exception type, never credentials, SMTP responses or message bodies.
            delivery.failed(
                e.getClass().getSimpleName(),
                settings.getMaxAttempts(),
                settings.getRetrySeconds()
            );
        }
        // SMTP and MySQL cannot commit atomically: a crash after acceptance may cause a duplicate.
    }
}
