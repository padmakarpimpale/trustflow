package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    List<AuditEntry> findByCaseRecordIdOrderByCreatedAtAsc(Long caseId);
}
