package com.padmakar.trustflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "trustflow.ai.mode=demo",
        "spring.datasource.url=jdbc:h2:mem:react;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
@AutoConfigureMockMvc
@Transactional
class ReactApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private final String expense =
        "{\"type\":\"EXPENSE\",\"title\":\"Business travel\",\"description\":\"Approved client meeting travel\",\"amount\":1200}";

    @Test
    void anonymousApiReturns401InsteadOfHtmlLogin() throws Exception {
        mvc.perform(get("/api/cases")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfTokenIsAvailableBeforeLogin() throws Exception {
        mvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void caseWriteRequiresCsrf() throws Exception {
        mvc.perform(
            post("/api/cases")
                .with(user("employee").roles("SUBMITTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(expense)
        ).andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotManagePolicies() throws Exception {
        mvc.perform(get("/api/admin/policies").with(user("employee").roles("SUBMITTER"))).andExpect(
            status().isForbidden()
        );
    }

    @Test
    void validationErrorsAreJson() throws Exception {
        mvc.perform(
            post("/api/cases")
                .with(user("employee").roles("SUBMITTER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EXPENSE\",\"title\":\"\",\"description\":\"short\"}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.title").exists());
    }

    @Test
    void reviewerCannotSubmit() throws Exception {
        mvc.perform(
            post("/api/cases")
                .with(user("reviewer").roles("REVIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(expense)
        ).andExpect(status().isForbidden());
    }

    @Test
    void ownerIsolationAndHumanOverride() throws Exception {
        var result = mvc
            .perform(
                post("/api/cases")
                    .with(user("employee").roles("SUBMITTER"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(expense)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();
        var record = mapper.readTree(result.getResponse().getContentAsString());
        String url = "/api/cases/" + record.get("id").asLong();
        mvc.perform(get(url).with(user("employee2").roles("SUBMITTER"))).andExpect(
            status().isForbidden()
        );
        mvc.perform(
            post(url + "/review")
                .with(user("reviewer").roles("REVIEWER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"version\":" +
                        record.get("version") +
                        ",\"decision\":\"REJECTED\",\"reason\":\"Budget unavailable\"}"
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.review.overridden").value(true))
            .andExpect(jsonPath("$.record.status").value("REJECTED"));
    }
}
