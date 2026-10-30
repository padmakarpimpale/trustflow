package com.padmakar.trustflow.service;

import com.padmakar.trustflow.ai.*;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.web.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

    private final CaseRecordRepository cases;
    private final UserAccountRepository users;
    private final PolicyRepository policies;
    private final ReviewRecordRepository reviews;
    private final AuditEntryRepository audit;
    private final AiClient ai;
    private final PolicyEngine engine;
    private final NotificationService notifications;

    public CaseService(
        CaseRecordRepository cases,
        UserAccountRepository users,
        PolicyRepository policies,
        ReviewRecordRepository reviews,
        AuditEntryRepository audit,
        AiClient ai,
        PolicyEngine engine,
        NotificationService notifications
    ) {
        this.cases = cases;
        this.users = users;
        this.policies = policies;
        this.reviews = reviews;
        this.audit = audit;
        this.ai = ai;
        this.engine = engine;
        this.notifications = notifications;
    }

    public UserAccount user(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    public CaseRecord findVisible(Long id, String username) {
        CaseRecord record = cases
            .findById(id)
            .orElseThrow(() -> new RecordNotFoundException("Case not found"));
        if (
            user(username).getRole() == Role.SUBMITTER &&
            !record.getOwner().getUsername().equals(username)
        ) throw new AccessDeniedException("You cannot view this case");
        return record;
    }

    @Transactional
    public CaseRecord submit(CaseForm form, String username) {
        UserAccount owner = user(username);
        if (owner.getRole() != Role.SUBMITTER) throw new AccessDeniedException(
            "Only submitters can create cases"
        );
        if (
            form.getType() == CaseType.EXPENSE && form.getAmount() == null
        ) throw new IllegalArgumentException("Enter an amount for an expense claim");
        if (
            form.getType() == CaseType.ACCESS &&
            !List.of("STANDARD", "ADMIN").contains(
                form.getAccessLevel() == null ? "" : form.getAccessLevel()
            )
        ) throw new IllegalArgumentException("Select an access level for an access request");
        Prediction prediction;
        boolean aiFailed = false;
        try {
            prediction = ai.predict(form);
        } catch (RuntimeException e) {
            aiFailed = true;
            prediction = new Prediction(
                Recommendation.REVIEW,
                0,
                "AI unavailable. A reviewer must decide.",
                "unavailable"
            );
        }
        Policy policy = policies.findByType(form.getType()).orElseThrow();
        String reason = engine.reviewReason(form, policy, prediction, aiFailed);
        CaseRecord record = new CaseRecord();
        record.setOwner(owner);
        record.setType(form.getType());
        record.setTitle(form.getTitle().trim());
        record.setDescription(form.getDescription().trim());
        record.setAmount(form.getType() == CaseType.EXPENSE ? form.getAmount() : null);
        record.setAccessLevel(form.getType() == CaseType.ACCESS ? form.getAccessLevel() : null);
        record.setRecommendation(prediction.recommendation());
        record.setConfidence(prediction.confidence());
        record.setExplanation(prediction.explanation());
        record.setModelVersion(prediction.modelVersion());
        record.setPolicyVersion(policy.getVersion());
        record.setPolicyAmountThreshold(policy.getAmountThreshold());
        record.setPolicyMinConfidence(policy.getMinConfidence());
        record.setPolicyAlwaysReview(policy.isAlwaysReview());
        record.setRoutingVersion(TextReviewGuard.VERSION);
        record.setEscalationReason(reason.isEmpty() ? "Within policy: automatic approval" : reason);
        record.setCreatedAt(Instant.now());
        record.setStatus(reason.isEmpty() ? CaseStatus.APPROVED : CaseStatus.PENDING_REVIEW);
        if (record.getStatus() == CaseStatus.APPROVED) record.setResolvedAt(Instant.now());
        cases.save(record);
        log(record, username, "SUBMITTED", "Case submitted");
        log(
            record,
            "SYSTEM",
            "EVALUATED",
            "Model=" +
                prediction.modelVersion() +
                "; policy=" +
                policy.getVersion() +
                "; routing=" +
                TextReviewGuard.VERSION +
                "; " +
                record.getEscalationReason()
        );
        notifications.send(
            owner,
            record.getId(),
            "Case #" + record.getId() + ": " + record.getStatus()
        );
        if (
            record.getStatus() == CaseStatus.PENDING_REVIEW
        ) for (UserAccount reviewer : users.findByRole(Role.REVIEWER))
            notifications.send(
                reviewer,
                record.getId(),
                "Case #" + record.getId() + " needs review"
            );
        return record;
    }

    @Transactional
    public void review(Long id, ReviewForm form, String username) {
        UserAccount reviewer = user(username);
        if (reviewer.getRole() != Role.REVIEWER) throw new AccessDeniedException(
            "Reviewer role required"
        );
        CaseRecord record = cases
            .findById(id)
            .orElseThrow(() -> new RecordNotFoundException("Case not found"));
        if (record.getOwner().getId().equals(reviewer.getId())) throw new AccessDeniedException(
            "Self review is not allowed"
        );
        if (
            record.getStatus() != CaseStatus.PENDING_REVIEW ||
            !Objects.equals(record.getVersion(), form.version())
        ) throw new ConflictException("Case changed or was already reviewed. Refresh the page.");
        CaseStatus decision = CaseStatus.valueOf(form.decision());
        if (decision == CaseStatus.PENDING_REVIEW) throw new IllegalArgumentException(
            "Choose approve or reject"
        );
        record.setStatus(decision);
        record.setResolvedAt(Instant.now());
        ReviewRecord review = new ReviewRecord();
        review.setCaseRecord(record);
        review.setReviewer(reviewer);
        review.setDecision(decision);
        review.setReason(form.reason().trim());
        review.setCreatedAt(Instant.now());
        boolean overridden =
            (record.getRecommendation() == Recommendation.APPROVE &&
                decision == CaseStatus.REJECTED) ||
            (record.getRecommendation() == Recommendation.REJECT &&
                decision == CaseStatus.APPROVED);
        review.setOverridden(overridden);
        reviews.save(review);
        log(
            record,
            username,
            overridden ? "OVERRIDDEN" : "REVIEWED",
            decision + ": " + form.reason().trim()
        );
        notifications.send(
            record.getOwner(),
            id,
            "Case #" + id + " was " + decision.toString().toLowerCase()
        );
        cases.flush(); // @Version rejects a concurrent update; the whole transaction then rolls
        // back.
    }

    private void log(CaseRecord record, String actor, String action, String detail) {
        AuditEntry entry = new AuditEntry();
        entry.setCaseRecord(record);
        entry.setActor(actor);
        entry.setAction(action);
        entry.setDetail(detail);
        entry.setCreatedAt(Instant.now());
        audit.save(entry);
    }
}
