package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.service.*;
import com.padmakar.trustflow.web.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "trustflow.ai.mode=demo",
        "spring.datasource.url=jdbc:h2:mem:workflow;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
@AutoConfigureMockMvc
@Transactional
class WorkflowTest {

    @Autowired
    CaseService service;

    @Autowired
    ReviewRecordRepository reviews;

    @Autowired
    AuditEntryRepository audit;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    NotificationService notificationService;

    @Autowired
    MockMvc mvc;

    CaseForm expense(String amount, String text) {
        CaseForm form = new CaseForm();
        form.setType(CaseType.EXPENSE);
        form.setTitle("Conference travel");
        form.setAmount(new BigDecimal(amount));
        form.setDescription(text);
        return form;
    }

    @Test
    void lowExpenseIsAutomaticallyApproved() {
        var record = service.submit(
            expense("80", "Business travel for client meeting"),
            "employee"
        );
        assertEquals(CaseStatus.APPROVED, record.getStatus());
        assertNotNull(record.getResolvedAt());
        assertEquals(2, audit.findByCaseRecordIdOrderByCreatedAtAsc(record.getId()).size());
    }

    @Test
    void highExpenseRequiresReview() {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        assertEquals(CaseStatus.PENDING_REVIEW, record.getStatus());
        assertTrue(record.getEscalationReason().contains("Amount exceeds"));
        assertFalse(
            notifications.findByRecipientUsernameOrderByCreatedAtDesc("reviewer").isEmpty()
        );
    }

    @Test
    void thresholdBoundaryIsNotEscalated() {
        assertEquals(
            CaseStatus.APPROVED,
            service
                .submit(expense("500", "Business travel for client meeting"), "employee")
                .getStatus()
        );
    }

    @Test
    void uncertaintyRequiresReview() {
        assertEquals(
            CaseStatus.PENDING_REVIEW,
            service.submit(expense("50", "The purpose is unclear today"), "employee").getStatus()
        );
    }

    @Test
    void rejectionRecommendationDoesNotAutoReject() {
        assertEquals(
            CaseStatus.PENDING_REVIEW,
            service.submit(expense("50", "This is for personal use only"), "employee").getStatus()
        );
    }

    @Test
    void reviewerCanOverrideAndReasonIsAudited() {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        service.review(
            record.getId(),
            new ReviewForm(record.getVersion(), "REJECTED", "Budget not available"),
            "reviewer"
        );
        var review = reviews.findByCaseRecordId(record.getId()).orElseThrow();
        assertTrue(review.isOverridden());
        assertEquals("Budget not available", review.getReason());
        assertEquals(3, audit.findByCaseRecordIdOrderByCreatedAtAsc(record.getId()).size());
    }

    @Test
    void secondFinalDecisionIsBlocked() {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        var request = new ReviewForm(
            record.getVersion(),
            "APPROVED",
            "Manager confirmed business purpose"
        );
        service.review(record.getId(), request, "reviewer");
        assertThrows(IllegalArgumentException.class, () ->
            service.review(record.getId(), request, "reviewer2")
        );
    }

    @Test
    void staleVersionIsBlocked() {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        assertThrows(IllegalArgumentException.class, () ->
            service.review(
                record.getId(),
                new ReviewForm(99L, "APPROVED", "Valid purpose"),
                "reviewer"
            )
        );
    }

    @Test
    void employeesCannotSeeOtherEmployeesCases() {
        var record = service.submit(
            expense("50", "Business travel for client meeting"),
            "employee"
        );
        assertThrows(AccessDeniedException.class, () ->
            service.findVisible(record.getId(), "employee2")
        );
    }

    @Test
    void employeeCannotReview() {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        assertThrows(AccessDeniedException.class, () ->
            service.review(
                record.getId(),
                new ReviewForm(record.getVersion(), "APPROVED", "Valid purpose"),
                "employee"
            )
        );
    }

    @Test
    void missingExpenseAmountIsBlocked() {
        CaseForm form = expense("50", "Business travel for client meeting");
        form.setAmount(null);
        assertThrows(IllegalArgumentException.class, () -> service.submit(form, "employee"));
    }

    @Test
    void duplicateNotificationIsIgnored() {
        var notice = new NotificationService.Notice(
            "unique-test-event",
            service.user("employee").getId(),
            1L,
            "Test notice"
        );
        notificationService.accept(notice);
        long count = notifications.count();
        notificationService.accept(notice);
        assertEquals(count, notifications.count());
    }

    @Test
    void accessRequestRequiresReview() {
        CaseForm form = new CaseForm();
        form.setType(CaseType.ACCESS);
        form.setTitle("System access");
        form.setDescription("Access needed for business reporting");
        form.setAccessLevel("ADMIN");
        var record = service.submit(form, "employee");
        assertEquals(CaseStatus.PENDING_REVIEW, record.getStatus());
        assertTrue(record.getEscalationReason().contains("Privileged"));
    }

    @Test
    void anonymousUserMustSignIn() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    @Test
    void csrfRequiredForChanges() throws Exception {
        mvc.perform(post("/cases/new").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().isForbidden()
        );
    }

    @Test
    void adminEndpointRejectsEmployee() throws Exception {
        mvc.perform(get("/admin/policies").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().isForbidden()
        );
    }

    @Test
    void negativeAmountFailsValidation() throws Exception {
        mvc.perform(
            post("/api/cases")
                .with(user("employee").roles("SUBMITTER"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"type\":\"EXPENSE\",\"title\":\"Invalid claim\",\"description\":\"Business travel justification\",\"amount\":-1}"
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.amount").exists());
    }

    @Test
    void allScreensRender() throws Exception {
        var record = service.submit(
            expense("1200", "Business travel for client meeting"),
            "employee"
        );
        mvc.perform(get("/legacy").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().is3xxRedirection()
        );
        mvc.perform(get("/cases/new").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().is3xxRedirection()
        );
        mvc.perform(
            get("/cases/" + record.getId()).with(user("reviewer").roles("REVIEWER"))
        ).andExpect(status().is3xxRedirection());
        mvc.perform(get("/notifications").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().is3xxRedirection()
        );
        mvc.perform(get("/admin/policies").with(user("admin").roles("ADMIN"))).andExpect(
            status().is3xxRedirection()
        );
        mvc.perform(get("/legacy").with(user("admin").roles("ADMIN"))).andExpect(
            status().is3xxRedirection()
        );
    }

    @Test
    void apiReturnsOnlyOwnCases() throws Exception {
        service.submit(expense("1200", "Business travel for client meeting"), "employee");
        mvc.perform(get("/api/cases").with(user("employee2").roles("SUBMITTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
