package com.padmakar.trustflow.config;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.*;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "trustflow.seed-demo", havingValue = "true")
public class DemoData implements CommandLineRunner {

    private final UserAccountRepository users;
    private final PolicyRepository policies;
    private final PasswordEncoder encoder;

    public DemoData(
        UserAccountRepository users,
        PolicyRepository policies,
        PasswordEncoder encoder
    ) {
        this.users = users;
        this.policies = policies;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        addUser("employee", Role.SUBMITTER);
        addUser("employee2", Role.SUBMITTER);
        addUser("reviewer", Role.REVIEWER);
        addUser("reviewer2", Role.REVIEWER);
        addUser("admin", Role.ADMIN);
        for (CaseType type : CaseType.values()) {
            if (policies.findByType(type).isPresent()) continue;
            Policy policy = new Policy();
            policy.setType(type);
            policy.setAmountThreshold(new BigDecimal("500.00"));
            policy.setMinConfidence(0.80);
            policy.setAlwaysReview(type == CaseType.ACCESS);
            policies.save(policy);
        }
    }

    private void addUser(String username, Role role) {
        if (users.findByUsername(username).isPresent()) return;
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setRole(role);
        user.setPassword(encoder.encode("TrustFlow123!"));
        users.save(user);
    }
}
