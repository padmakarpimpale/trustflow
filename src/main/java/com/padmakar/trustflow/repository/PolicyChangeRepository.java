package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.PolicyChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChangeRepository extends JpaRepository<PolicyChange, Long> {
    Page<PolicyChange> findByPolicyId(Long policyId, Pageable pageable);
}
