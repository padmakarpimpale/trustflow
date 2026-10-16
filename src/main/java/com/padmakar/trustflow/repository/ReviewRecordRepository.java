package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {
    Optional<ReviewRecord> findByCaseRecordId(Long caseId);
}
