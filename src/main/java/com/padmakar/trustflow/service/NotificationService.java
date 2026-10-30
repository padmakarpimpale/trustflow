package com.padmakar.trustflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    public record Notice(String eventId, Long recipientId, Long caseId, String message) {}

    private final NotificationRepository notifications;
    private final OutboxEventRepository outbox;
    private final UserAccountRepository users;
    private final ObjectMapper mapper;
    private final String mode;

    public NotificationService(
        NotificationRepository notifications,
        OutboxEventRepository outbox,
        UserAccountRepository users,
        ObjectMapper mapper,
        @Value("${trustflow.notifications.mode}") String mode
    ) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.users = users;
        this.mapper = mapper;
        this.mode = mode;
    }

    @Transactional
    public void send(UserAccount user, Long caseId, String message) {
        Notice notice = new Notice(UUID.randomUUID().toString(), user.getId(), caseId, message);
        if ("kafka".equals(mode)) {
            try {
                OutboxEvent event = new OutboxEvent();
                event.setEventId(notice.eventId());
                event.setPayload(mapper.writeValueAsString(notice));
                event.setCreatedAt(Instant.now());
                outbox.save(event);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            accept(notice);
        }
    }

    @Transactional
    public void accept(Notice notice) {
        if (
            notice == null ||
            notice.eventId() == null ||
            notice.eventId().length() > 36 ||
            notice.eventId().isBlank() ||
            notice.recipientId() == null ||
            notice.caseId() == null ||
            notice.message() == null ||
            notice.message().isBlank() ||
            notice.message().length() > 500
        ) {
            throw new IllegalArgumentException("Invalid notification payload");
        }
        UserAccount recipient = users.lockById(notice.recipientId()).orElseThrow();
        if (notifications.existsByEventId(notice.eventId())) return;
        Notification notification = new Notification();
        notification.setEventId(notice.eventId());
        notification.setRecipient(recipient);
        notification.setCaseId(notice.caseId());
        notification.setMessage(notice.message());
        notification.setCreatedAt(Instant.now());
        notifications.save(notification);
    }

    @Transactional
    public void markRead(Long id, String username) {
        Notification notification = notifications.findById(id).orElseThrow();
        if (
            !notification.getRecipient().getUsername().equals(username)
        ) throw new org.springframework.security.access.AccessDeniedException(
            "This notification belongs to another user"
        );
        notification.setReadFlag(true);
    }
}
