package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRecordRepository extends JpaRepository<CaseRecord, Long> {
    List<CaseRecord> findByOwnerUsernameOrderByCreatedAtDesc(String username);

    List<CaseRecord> findAllByOrderByCreatedAtDesc();
}
