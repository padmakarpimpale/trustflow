package com.padmakar.trustflow.web;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.service.*;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PageController {

    private final CaseService service;
    private final AuditEntryRepository audit;
    private final ReviewRecordRepository reviews;
    private final PolicyRepository policies;
    private final PolicyService policyService;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final String aiMode;

    public PageController(
        CaseService service,
        AuditEntryRepository audit,
        ReviewRecordRepository reviews,
        PolicyRepository policies,
        PolicyService policyService,
        NotificationRepository notifications,
        NotificationService notificationService,
        @Value("${trustflow.ai.mode}") String aiMode
    ) {
        this.service = service;
        this.audit = audit;
        this.reviews = reviews;
        this.policies = policies;
        this.policyService = policyService;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.aiMode = aiMode;
    }

    @ModelAttribute
    public void common(Model model, Principal principal) {
        model.addAttribute("aiMode", aiMode);
        if (principal != null) model.addAttribute("currentUser", service.user(principal.getName()));
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/app/index.html";
    }

    @GetMapping("/")
    public String app() {
        return "redirect:/app/index.html";
    }

    @GetMapping("/legacy")
    public String dashboard(Model model, Principal principal) {
        var records = service.visibleCases(principal.getName());
        model.addAttribute("cases", records);
        model.addAttribute("total", records.size());
        model.addAttribute(
            "pending",
            records
                .stream()
                .filter(c -> c.getStatus() == CaseStatus.PENDING_REVIEW)
                .count()
        );
        model.addAttribute(
            "approved",
            records
                .stream()
                .filter(c -> c.getStatus() == CaseStatus.APPROVED)
                .count()
        );
        model.addAttribute(
            "rejected",
            records
                .stream()
                .filter(c -> c.getStatus() == CaseStatus.REJECTED)
                .count()
        );
        if (service.user(principal.getName()).getRole() == Role.ADMIN) {
            var decisions = reviews.findAll();
            model.addAttribute(
                "overrides",
                decisions.stream().filter(ReviewRecord::isOverridden).count()
            );
            model.addAttribute("reviewCount", decisions.size());
            double minutes =
                decisions
                    .stream()
                    .mapToLong(r ->
                        Duration.between(
                            r.getCaseRecord().getCreatedAt(),
                            r.getCreatedAt()
                        ).toSeconds()
                    )
                    .average()
                    .orElse(0) /
                60;
            model.addAttribute("reviewMinutes", Math.round(minutes * 10.0) / 10.0);
        }
        return "dashboard";
    }

    @GetMapping("/cases/new")
    public String newCase(Model model) {
        model.addAttribute("caseForm", new CaseForm());
        return "new-case";
    }

    @PostMapping("/cases/new")
    public String create(
        @Valid @ModelAttribute CaseForm caseForm,
        BindingResult errors,
        Principal principal,
        RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) return "new-case";
        try {
            var record = service.submit(caseForm, principal.getName());
            redirect.addFlashAttribute("success", "Case submitted successfully");
            return "redirect:/cases/" + record.getId();
        } catch (IllegalArgumentException e) {
            errors.reject("invalid", e.getMessage());
            return "new-case";
        }
    }

    @GetMapping("/cases/{id}")
    public String detail(@PathVariable Long id, Model model, Principal principal) {
        model.addAttribute("case", service.findVisible(id, principal.getName()));
        model.addAttribute("audit", audit.findByCaseRecordIdOrderByCreatedAtAsc(id));
        model.addAttribute("review", reviews.findByCaseRecordId(id).orElse(null));
        return "case-detail";
    }

    @PostMapping("/cases/{id}/review")
    public String review(
        @PathVariable Long id,
        @Valid @ModelAttribute ReviewForm form,
        BindingResult errors,
        Principal principal,
        RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute(
                "error",
                "Choose a decision and enter a reason (5-1000 characters)."
            );
        } else {
            service.review(id, form, principal.getName());
            redirect.addFlashAttribute("success", "Decision recorded");
        }
        return "redirect:/cases/" + id;
    }

    @GetMapping("/admin/policies")
    public String policies(Model model) {
        model.addAttribute("policies", policies.findAll());
        return "policies";
    }

    @PostMapping("/admin/policies/{id}")
    public String savePolicy(
        @PathVariable Long id,
        @Valid @ModelAttribute PolicyForm form,
        BindingResult errors,
        RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) redirect.addFlashAttribute(
            "error",
            "Use a valid amount and score between 0 and 1."
        );
        else {
            policyService.update(id, form);
            redirect.addFlashAttribute("success", "Policy updated. It applies to new cases only.");
        }
        return "redirect:/admin/policies";
    }

    @GetMapping("/notifications")
    public String notices(Model model, Principal principal) {
        model.addAttribute(
            "notifications",
            notifications.findByRecipientUsernameOrderByCreatedAtDesc(principal.getName())
        );
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String read(@PathVariable Long id, Principal principal) {
        notificationService.markRead(id, principal.getName());
        return "redirect:/notifications";
    }
}
