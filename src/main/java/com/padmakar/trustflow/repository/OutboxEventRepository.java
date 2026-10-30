package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    @org.springframework.data.jpa.repository.Lock(
        jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
    )
    @org.springframework.data.jpa.repository.Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    List<OutboxEvent> findTop20ByPublishedFalseOrderByIdAsc();
}
