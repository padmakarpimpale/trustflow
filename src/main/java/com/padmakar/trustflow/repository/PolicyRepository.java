package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Optional<Policy> findByType(CaseType type);
}
