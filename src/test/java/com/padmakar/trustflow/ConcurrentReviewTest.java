package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.service.CaseService;
import com.padmakar.trustflow.web.*;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
        "trustflow.ai.mode=demo",
        "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
class ConcurrentReviewTest {

    @Autowired
    CaseService service;

    @Autowired
    CaseRecordRepository cases;

    @Autowired
    ReviewRecordRepository reviews;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void simultaneousReviewersCannotCreateTwoDecisions() throws Exception {
        CaseForm form = new CaseForm();
        form.setType(CaseType.EXPENSE);
        form.setTitle("Concurrent review test");
        form.setDescription("Business travel for a client meeting");
        form.setAmount(new BigDecimal("1200"));
        CaseRecord record = service.submit(form, "employee");
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> task = () -> {
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        CaseRecord loaded = cases.findById(record.getId()).orElseThrow();
                        Long version = loaded.getVersion();
                        ready.countDown();
                        try {
                            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(
                                "Review barrier timeout"
                            );
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        service.review(
                            record.getId(),
                            new ReviewForm(version, "APPROVED", "Confirmed business purpose"),
                            "reviewer"
                        );
                    });
                    return true;
                } catch (org.springframework.dao.DataAccessException | IllegalArgumentException e) {
                    return false;
                }
            };
            Future<Boolean> first = threads.submit(task);
            Future<Boolean> second = threads.submit(task);
            int successes =
                (first.get(15, TimeUnit.SECONDS) ? 1 : 0) +
                (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, reviews.count());
            assertEquals(
                CaseStatus.APPROVED,
                cases.findById(record.getId()).orElseThrow().getStatus()
            );
        } finally {
            threads.shutdownNow();
        }
    }
}
