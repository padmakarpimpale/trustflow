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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "trustflow.ai.mode=demo",
        "spring.datasource.url=jdbc:h2:mem:upgrade;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
@AutoConfigureMockMvc
@Transactional
class ArchitectureUpgradeTest {

    @Autowired
    CaseService cases;

    @Autowired
    CaseQueryService queries;

    @Autowired
    PolicyService policyService;

    @Autowired
    PolicyRepository policies;

    @Autowired
    PolicyChangeRepository changes;

    @Autowired
    MockMvc mvc;

    private CaseForm expense(String title) {
        var form = new CaseForm();
        form.setType(CaseType.EXPENSE);
        form.setTitle(title);
        form.setDescription("Approved travel for client meeting");
        form.setAmount(new BigDecimal("80"));
        return form;
    }

    @Test
    void policySnapshotSurvivesPolicyEdit() {
        var record = cases.submit(expense("Before change"), "employee");
        var policy = policies.findByType(CaseType.EXPENSE).orElseThrow();
        var original = record.getPolicyAmountThreshold();
        policyService.update(
            policy.getId(),
            new PolicyForm(policy.getVersion(), new BigDecimal("20"), .95, true),
            "admin"
        );
        assertEquals(
            original,
            cases.findVisible(record.getId(), "employee").getPolicyAmountThreshold()
        );
        assertEquals(.8, record.getPolicyMinConfidence());
        assertFalse(record.getPolicyAlwaysReview());
        assertEquals("text-guard-v1", record.getRoutingVersion());
        var history = changes.findAll();
        assertEquals(1, history.size());
        assertEquals("admin", history.get(0).getActor());
        assertTrue(history.get(0).getBeforeValues().contains("500"));
        assertTrue(history.get(0).getAfterValues().contains("20"));
        assertTrue(cases.submit(expense("After change"), "employee").getPolicyAlwaysReview());
    }

    @Test
    void nonAdminCannotChangePolicyThroughService() {
        var policy = policies.findByType(CaseType.EXPENSE).orElseThrow();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
            policyService.update(
                policy.getId(),
                new PolicyForm(policy.getVersion(), new BigDecimal("20"), .8, false),
                "employee"
            )
        );
        assertEquals(0, changes.count());
    }

    @Test
    void paginationFiltersPreserveOwnerIsolation() {
        cases.submit(expense("Visible A"), "employee");
        cases.submit(expense("Visible B"), "employee");
        cases.submit(expense("Hidden"), "employee2");
        var page = queries.search("employee", null, CaseType.EXPENSE, "Visible", 0, 1, "newest");
        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(
            0,
            queries.search("employee", null, null, "Hidden", 0, 8, "newest").getTotalElements()
        );
        assertEquals(2L, queries.summary("employee").get("total"));
    }

    @Test
    void wildcardSearchIsLiteral() {
        cases.submit(expense("Normal title"), "employee");
        assertEquals(
            0,
            queries.search("employee", null, null, "%", 0, 8, "newest").getTotalElements()
        );
    }

    @Test
    void missingCaseIs404() throws Exception {
        mvc.perform(get("/api/cases/999999").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().isNotFound()
        );
    }

    @Test
    void staleReviewIs409() throws Exception {
        var form = expense("Review race");
        form.setAmount(new BigDecimal("1200"));
        var record = cases.submit(form, "employee");
        mvc.perform(
            post("/api/cases/" + record.getId() + "/review")
                .with(user("reviewer").roles("REVIEWER"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"version\":99,\"decision\":\"APPROVED\",\"reason\":\"Checked evidence\"}"
                )
        ).andExpect(status().isConflict());
    }

    @Test
    void invalidPagingAndEnumsAre400() throws Exception {
        for (String query : new String[] {
            "size=101",
            "page=-1",
            "status=OTHER",
            "sort=password",
        }) {
            mvc.perform(
                get("/api/cases/page?" + query).with(user("employee").roles("SUBMITTER"))
            ).andExpect(status().isBadRequest());
        }
    }

    @Test
    void correlationIdIsReturned() throws Exception {
        mvc.perform(get("/api/csrf").header("X-Request-ID", "demo-request-123")).andExpect(
            header().string("X-Request-ID", "demo-request-123")
        );
    }

    @Test
    void healthDoesNotExposeDatabaseDetailsAnonymously() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components").doesNotExist());
    }
}
