package com.padmakar.trustflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_delivery")
public class EmailDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private int attempts;

    @Column(length = 100)
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant nextAttemptAt = Instant.now();

    private Instant sentAt;

    protected EmailDelivery() {}

    public EmailDelivery(
        String eventId,
        Long caseId,
        String username,
        String recipient,
        String subject,
        String body
    ) {
        this.eventId = eventId;
        this.caseId = caseId;
        this.username = username;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public String getUsername() {
        return username;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void beginAttempt() {
        attempts++;
    }

    public void sent() {
        status = "SENT";
        sentAt = Instant.now();
        lastError = null;
    }

    public void failed(String error, int maxAttempts, long retrySeconds) {
        lastError = error;
        status = attempts >= maxAttempts ? "FAILED" : "PENDING";
        nextAttemptAt = Instant.now().plusSeconds(retrySeconds * (1L << Math.min(attempts - 1, 6)));
    }
}
