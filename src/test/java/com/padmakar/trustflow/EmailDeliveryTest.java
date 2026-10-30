package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.padmakar.trustflow.config.EmailSettings;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.service.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDeliveryTest {

    EmailDeliveryRepository deliveries;
    UserAccountRepository users;
    EmailSettings settings;
    EmailQueue queue;
    JavaMailSender mail;
    EmailDispatcher dispatcher;

    @BeforeEach
    void setup() {
        deliveries = mock(EmailDeliveryRepository.class);
        users = mock(UserAccountRepository.class);
        mail = mock(JavaMailSender.class);
        settings = new EmailSettings();
        settings.setFrom("sender@example.test");
        settings.setRecipients(
            Map.of("employee", "inbox@example.test", "reviewer", "inbox@example.test")
        );
        queue = new EmailQueue(deliveries, users, settings);
        dispatcher = new EmailDispatcher(deliveries, mail, settings);
        var reviewer = new UserAccount();
        reviewer.setUsername("reviewer");
        reviewer.setRole(Role.REVIEWER);
        var employee = new UserAccount();
        employee.setUsername("employee");
        employee.setRole(Role.SUBMITTER);
        when(users.lockById(1L)).thenReturn(Optional.of(reviewer));
        when(users.lockById(2L)).thenReturn(Optional.of(employee));
    }

    NotificationService.Notice event(String type, Long recipient, String reason) {
        return new NotificationService.Notice(
            "event-1",
            recipient,
            42L,
            "Notice",
            type,
            "Travel claim",
            "APPROVE",
            .9,
            reason
        );
    }

    @Test
    void reviewEmailContainsEventSnapshotAndLoginLink() {
        queue.enqueue(event("REVIEW_REQUIRED", 1L, null));
        var captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveries).save(captor.capture());
        var row = captor.getValue();
        assertEquals("inbox@example.test", row.getRecipient());
        assertTrue(row.getSubject().contains("requires review"));
        assertTrue(row.getBody().contains("PENDING_REVIEW"));
        assertTrue(row.getBody().contains("90.00%"));
        assertTrue(row.getBody().contains("/app/index.html#case/42"));
    }

    @Test
    void rejectionIncludesReasonAndFinalStatus() {
        queue.enqueue(event("CASE_REJECTED", 2L, "Budget is insufficient"));
        var captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveries).save(captor.capture());
        assertTrue(captor.getValue().getBody().contains("Status at event time: REJECTED"));
        assertTrue(captor.getValue().getBody().contains("Budget is insufficient"));
    }

    @Test
    void approvalIsAddressedToEmployee() {
        queue.enqueue(event("CASE_APPROVED", 2L, "Reviewed and approved"));
        var captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveries).save(captor.capture());
        assertEquals("employee", captor.getValue().getUsername());
        assertTrue(captor.getValue().getSubject().endsWith("approved"));
    }

    @Test
    void repeatedEventDoesNotCreateAnotherDelivery() {
        when(deliveries.existsByEventId("event-1")).thenReturn(true);
        queue.enqueue(event("REVIEW_REQUIRED", 1L, null));
        verify(deliveries, never()).save(any());
    }

    @Test
    void legacyAndSubmissionEventsDoNotSendEmail() {
        queue.enqueue(new NotificationService.Notice("old", 1L, 42L, "old notification"));
        queue.enqueue(event("CASE_SUBMITTED", 2L, null));
        verifyNoInteractions(deliveries, users);
    }

    @Test
    void unmappedUsersKeepInAppOnly() {
        settings.setRecipients(Map.of());
        queue.enqueue(event("REVIEW_REQUIRED", 1L, null));
        verify(deliveries, never()).save(any());
    }

    @Test
    void malformedAndWrongRoleEventsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            queue.enqueue(event("UNKNOWN", 1L, null))
        );
        assertThrows(IllegalArgumentException.class, () ->
            queue.enqueue(event("REVIEW_REQUIRED", 2L, null))
        );
        var bad = new NotificationService.Notice(
            "bad",
            1L,
            42L,
            "Notice",
            "REVIEW_REQUIRED",
            "Travel",
            "APPROVE",
            Double.NaN,
            null
        );
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(bad));
        verify(deliveries, never()).save(any());
    }

    EmailDelivery pending() {
        var row = new EmailDelivery(
            "event-1",
            42L,
            "employee",
            "inbox@example.test",
            "Subject",
            "Body"
        );
        when(deliveries.lockById(1L)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void smtpFailurePersistsRetryThenRecoveryMarksSent() {
        var row = pending();
        doThrow(new MailSendException("Sensitive server response"))
            .doNothing()
            .when(mail)
            .send(any(SimpleMailMessage.class));
        dispatcher.sendOne(1L);
        assertEquals("PENDING", row.getStatus());
        assertEquals(1, row.getAttempts());
        assertEquals("MailSendException", row.getLastError());
        assertTrue(row.getNextAttemptAt().isAfter(Instant.now()));
        dispatcher.sendOne(1L);
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        ReflectionTestUtils.setField(row, "nextAttemptAt", Instant.now().minusSeconds(1));
        dispatcher.sendOne(1L);
        assertEquals("SENT", row.getStatus());
        assertEquals(2, row.getAttempts());
        assertNotNull(row.getSentAt());
        assertNull(row.getLastError());
        dispatcher.sendOne(1L);
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void exhaustedAttemptsAreRetainedAsFailed() {
        settings.setMaxAttempts(1);
        var row = pending();
        doThrow(new MailAuthenticationException("Secret"))
            .when(mail)
            .send(any(SimpleMailMessage.class));
        dispatcher.sendOne(1L);
        dispatcher.sendOne(1L);
        assertEquals("FAILED", row.getStatus());
        assertEquals(1, row.getAttempts());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }
}
