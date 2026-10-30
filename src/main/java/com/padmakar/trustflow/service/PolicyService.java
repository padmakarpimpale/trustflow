package com.padmakar.trustflow.service;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import com.padmakar.trustflow.web.*;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {

    private final PolicyRepository policies;
    private final PolicyChangeRepository history;
    private final UserAccountRepository users;

    public PolicyService(
        PolicyRepository policies,
        PolicyChangeRepository history,
        UserAccountRepository users
    ) {
        this.policies = policies;
        this.history = history;
        this.users = users;
    }

    @Transactional
    public void update(Long id, PolicyForm form, String username) {
        if (users.findByUsername(username).orElseThrow().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
        Policy policy = policies
            .findById(id)
            .orElseThrow(() -> new RecordNotFoundException("Policy not found"));
        if (!Objects.equals(policy.getVersion(), form.version())) {
            throw new ConflictException("Policy changed. Reload before saving.");
        }
        String before = values(policy);
        policy.setAmountThreshold(form.amountThreshold());
        policy.setMinConfidence(form.minConfidence());
        policy.setAlwaysReview(form.alwaysReview());
        policies.flush();
        history.save(new PolicyChange(id, username, before, values(policy)));
    }

    private String values(Policy p) {
        return (
            "version=" +
            p.getVersion() +
            "; type=" +
            p.getType() +
            "; amountThreshold=" +
            p.getAmountThreshold() +
            "; minConfidence=" +
            p.getMinConfidence() +
            "; alwaysReview=" +
            p.isAlwaysReview()
        );
    }
}
