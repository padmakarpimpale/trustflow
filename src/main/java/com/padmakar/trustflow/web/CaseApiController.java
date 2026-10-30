package com.padmakar.trustflow.web;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.service.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Explicit DTOs keep passwords and persistence relationships out of JSON. */
@RestController
@RequestMapping("/api")
public class CaseApiController {

    private final CaseService cases;
    private final CaseQueryService queries;
    private final PolicyChangeRepository policyChanges;
    private final AuditEntryRepository audit;
    private final ReviewRecordRepository reviews;
    private final PolicyRepository policies;
    private final PolicyService policyService;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final String aiMode;

    public CaseApiController(
        CaseService cases,
        CaseQueryService queries,
        PolicyChangeRepository policyChanges,
        AuditEntryRepository audit,
        ReviewRecordRepository reviews,
        PolicyRepository policies,
        PolicyService policyService,
        NotificationRepository notifications,
        NotificationService notificationService,
        @Value("${trustflow.ai.mode}") String aiMode
    ) {
        this.cases = cases;
        this.queries = queries;
        this.policyChanges = policyChanges;
        this.audit = audit;
        this.reviews = reviews;
        this.policies = policies;
        this.policyService = policyService;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.aiMode = aiMode;
    }

    public record CaseView(
        Long id,
        Long version,
        String title,
        String type,
        String status,
        String owner,
        String description,
        BigDecimal amount,
        String accessLevel,
        String recommendation,
        Double confidence,
        String explanation,
        String modelVersion,
        Long policyVersion,
        BigDecimal policyAmountThreshold,
        Double policyMinConfidence,
        Boolean policyAlwaysReview,
        String routingVersion,
        String escalationReason,
        Instant createdAt,
        Instant resolvedAt
    ) {
        static CaseView from(CaseRecord c) {
            return new CaseView(
                c.getId(),
                c.getVersion(),
                c.getTitle(),
                c.getType().name(),
                c.getStatus().name(),
                c.getOwner().getUsername(),
                c.getDescription(),
                c.getAmount(),
                c.getAccessLevel(),
                c.getRecommendation().name(),
                c.getConfidence(),
                c.getExplanation(),
                c.getModelVersion(),
                c.getPolicyVersion(),
                c.getPolicyAmountThreshold(),
                c.getPolicyMinConfidence(),
                c.getPolicyAlwaysReview(),
                c.getRoutingVersion(),
                c.getEscalationReason(),
                c.getCreatedAt(),
                c.getResolvedAt()
            );
        }
    }

    public record AuditView(String actor, String action, String detail, Instant createdAt) {}

    public record ReviewView(
        String reviewer,
        String decision,
        String reason,
        boolean overridden,
        Instant createdAt
    ) {}

    public record Detail(CaseView record, List<AuditView> audit, ReviewView review) {}

    public record NoticeView(
        Long id,
        Long caseId,
        String message,
        boolean read,
        Instant createdAt
    ) {}

    public record SessionView(String username, String role, String aiMode) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
            "token",
            token.getToken(),
            "headerName",
            token.getHeaderName(),
            "parameterName",
            token.getParameterName()
        );
    }

    @GetMapping("/session")
    public SessionView session(Principal principal) {
        return new SessionView(
            principal.getName(),
            cases.user(principal.getName()).getRole().name(),
            aiMode
        );
    }

    public record PageView<T>(List<T> content, int number, int totalPages, long totalElements) {}

    @GetMapping({ "/cases", "/cases/page" })
    public PageView<CaseView> page(
        Principal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "8") int size,
        @RequestParam(required = false) CaseStatus status,
        @RequestParam(required = false) CaseType type,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "newest") String sort
    ) {
        var result = queries.search(principal.getName(), status, type, q, page, size, sort);
        return new PageView<>(
            result.getContent().stream().map(CaseView::from).toList(),
            result.getNumber(),
            result.getTotalPages(),
            result.getTotalElements()
        );
    }

    @GetMapping("/cases/summary")
    public Map<String, Long> summary(Principal principal) {
        return queries.summary(principal.getName());
    }

    @PostMapping("/cases")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public CaseView create(@Valid @RequestBody CaseForm form, Principal principal) {
        return CaseView.from(cases.submit(form, principal.getName()));
    }

    @GetMapping("/cases/{id}")
    public Detail detail(@PathVariable Long id, Principal principal) {
        var record = cases.findVisible(id, principal.getName());
        var history = audit
            .findByCaseRecordIdOrderByCreatedAtAsc(id)
            .stream()
            .map(a -> new AuditView(a.getActor(), a.getAction(), a.getDetail(), a.getCreatedAt()))
            .toList();
        var decision = reviews
            .findByCaseRecordId(id)
            .map(r ->
                new ReviewView(
                    r.getReviewer().getUsername(),
                    r.getDecision().name(),
                    r.getReason(),
                    r.isOverridden(),
                    r.getCreatedAt()
                )
            )
            .orElse(null);
        return new Detail(CaseView.from(record), history, decision);
    }

    @PostMapping("/cases/{id}/review")
    public Detail review(
        @PathVariable Long id,
        @Valid @RequestBody ReviewForm form,
        Principal principal
    ) {
        cases.review(id, form, principal.getName());
        return detail(id, principal);
    }

    @GetMapping("/notifications")
    public List<NoticeView> notices(Principal principal) {
        return notifications
            .findByRecipientUsernameOrderByCreatedAtDesc(principal.getName())
            .stream()
            .map(n ->
                new NoticeView(
                    n.getId(),
                    n.getCaseId(),
                    n.getMessage(),
                    n.isReadFlag(),
                    n.getCreatedAt()
                )
            )
            .toList();
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, Principal principal) {
        notificationService.markRead(id, principal.getName());
    }

    @GetMapping("/admin/policies")
    public List<PolicyView> policies() {
        return policies.findAll().stream().map(PolicyView::from).toList();
    }

    public record PolicyView(
        Long id,
        Long version,
        CaseType type,
        BigDecimal amountThreshold,
        double minConfidence,
        boolean alwaysReview
    ) {
        static PolicyView from(Policy p) {
            return new PolicyView(
                p.getId(),
                p.getVersion(),
                p.getType(),
                p.getAmountThreshold(),
                p.getMinConfidence(),
                p.isAlwaysReview()
            );
        }
    }

    @GetMapping("/admin/policies/{id}/history")
    public PageView<PolicyChange> policyHistory(
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page
    ) {
        if (page < 0) throw new IllegalArgumentException("Invalid page");
        if (!policies.existsById(id)) throw new RecordNotFoundException("Policy not found");
        var result = policyChanges.findByPolicyId(
            id,
            org.springframework.data.domain.PageRequest.of(
                page,
                20,
                org.springframework.data.domain.Sort.by("id").descending()
            )
        );
        return new PageView<>(
            result.getContent(),
            result.getNumber(),
            result.getTotalPages(),
            result.getTotalElements()
        );
    }

    @PutMapping("/admin/policies/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void policy(
        @PathVariable Long id,
        @Valid @RequestBody PolicyForm form,
        Principal principal
    ) {
        if (!Double.isFinite(form.minConfidence())) throw new IllegalArgumentException(
            "Invalid score"
        );
        policyService.update(id, form, principal.getName());
    }

    @GetMapping("/admin/analytics")
    public Map<String, Object> analytics() {
        var decisions = reviews.findAll();
        double minutes =
            decisions
                .stream()
                .mapToLong(r ->
                    Duration.between(r.getCaseRecord().getCreatedAt(), r.getCreatedAt()).toSeconds()
                )
                .average()
                .orElse(0) / 60;
        return Map.of(
            "reviewCount",
            decisions.size(),
            "overrides",
            decisions.stream().filter(ReviewRecord::isOverridden).count(),
            "reviewMinutes",
            Math.round(minutes * 10.0) / 10.0
        );
    }
}
