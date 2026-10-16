package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.padmakar.trustflow.ai.AiClient;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.service.CaseService;
import com.padmakar.trustflow.web.CaseForm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "trustflow.ai.mode=demo",
        "spring.datasource.url=jdbc:h2:mem:failure;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
@Transactional
class AiFailureTest {

    @MockitoBean
    AiClient ai;

    @Autowired
    CaseService service;

    @Test
    void outageRoutesToHumanWithoutLosingCase() {
        when(ai.predict(any())).thenThrow(new IllegalStateException("timeout"));
        CaseForm form = new CaseForm();
        form.setType(CaseType.EXPENSE);
        form.setTitle("Travel");
        form.setAmount(new BigDecimal("30"));
        form.setDescription("Business travel for meeting");
        var record = service.submit(form, "employee");
        assertNotNull(record.getId());
        assertEquals(CaseStatus.PENDING_REVIEW, record.getStatus());
        assertEquals("unavailable", record.getModelVersion());
    }
}
