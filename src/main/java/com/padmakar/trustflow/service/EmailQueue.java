package com.padmakar.trustflow.service;

import com.padmakar.trustflow.config.EmailSettings;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "trustflow.email.enabled", havingValue = "true")
public class EmailQueue {

    private final EmailDeliveryRepository deliveries;
    private final UserAccountRepository users;
    private final EmailSettings settings;

    public EmailQueue(
        EmailDeliveryRepository deliveries,
        UserAccountRepository users,
        EmailSettings settings
    ) {
        this.deliveries = deliveries;
        this.users = users;
        this.settings = settings;
    }

    @Transactional
    public void enqueue(NotificationService.Notice event) {
        if (event == null) throw new IllegalArgumentException("Missing event");
        if (event.eventType() == null || "CASE_SUBMITTED".equals(event.eventType())) return;
        if (
            !Set.of("REVIEW_REQUIRED", "CASE_APPROVED", "CASE_REJECTED").contains(event.eventType())
        ) {
            throw new IllegalArgumentException("Unknown email event type");
        }
        if (
            event.eventId() == null ||
            event.eventId().isBlank() ||
            event.eventId().length() > 36 ||
            event.recipientId() == null ||
            event.caseId() == null ||
            event.caseId() <= 0 ||
            event.title() == null ||
            event.title().isBlank() ||
            event.title().length() > 120 ||
            event.confidence() == null ||
            !Double.isFinite(event.confidence()) ||
            event.confidence() < 0 ||
            event.confidence() > 1 ||
            event.recommendation() == null ||
            !Set.of("APPROVE", "REVIEW", "REJECT").contains(event.recommendation()) ||
            (event.decisionReason() != null && event.decisionReason().length() > 1000)
        ) {
            throw new IllegalArgumentException("Invalid email event");
        }
        // Serialize duplicate intake for a recipient, with a unique event ID as the final guard.
        var user = users.lockById(event.recipientId()).orElseThrow();
        var address = settings.getRecipients().get(user.getUsername());
        if (
            address == null || address.isBlank() || deliveries.existsByEventId(event.eventId())
        ) return;
        boolean review = "REVIEW_REQUIRED".equals(event.eventType());
        if (review && user.getRole() != Role.REVIEWER) throw new IllegalArgumentException(
            "Reviewer required"
        );
        if (!review && user.getRole() != Role.SUBMITTER) throw new IllegalArgumentException(
            "Submitter required"
        );
        String state = review ? "PENDING_REVIEW" : event.eventType().substring(5);
        String subject =
            "TrustFlow - Case #" +
            event.caseId() +
            (review ? " requires review" : " " + state.toLowerCase(Locale.ROOT));
        String body =
            "Hello " +
            user.getUsername() +
            ",\n\n" +
            "Case: " +
            event.title() +
            "\n" +
            "Case ID: " +
            event.caseId() +
            "\n" +
            "Status at event time: " +
            state +
            "\n" +
            "Model recommendation: " +
            event.recommendation() +
            "\n" +
            "Model score: " +
            String.format(Locale.ROOT, "%.2f%%", event.confidence() * 100) +
            "\n" +
            (event.decisionReason() == null
                ? ""
                : "Reviewer reason: " + event.decisionReason() + "\n") +
            "\nSign in to TrustFlow to view the current case or record a decision:\n" +
            settings.getBaseUrl().replaceAll("/+$", "") +
            "/app/index.html#case/" +
            event.caseId() +
            "\n\nThe model score is uncalibrated, not verified decision accuracy." +
            "\nThis email records an event; the case may have changed since then.\n";
        deliveries.save(
            new EmailDelivery(
                event.eventId(),
                event.caseId(),
                user.getUsername(),
                address,
                subject,
                body
            )
        );
    }
}
