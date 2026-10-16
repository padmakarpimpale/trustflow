package com.padmakar.trustflow.service;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.PolicyRepository;
import com.padmakar.trustflow.web.PolicyForm;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {

    private final PolicyRepository policies;

    public PolicyService(PolicyRepository policies) {
        this.policies = policies;
    }

    @Transactional
    public void update(Long id, PolicyForm form) {
        Policy policy = policies.findById(id).orElseThrow();
        if (
            !Objects.equals(policy.getVersion(), form.version())
        ) throw new IllegalArgumentException("Policy changed. Reload before saving.");
        policy.setAmountThreshold(form.amountThreshold());
        policy.setMinConfidence(form.minConfidence());
        policy.setAlwaysReview(form.alwaysReview());
        policies.flush();
    }
}
