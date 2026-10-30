package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRecordRepository
    extends
        JpaRepository<CaseRecord, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<CaseRecord> {}
