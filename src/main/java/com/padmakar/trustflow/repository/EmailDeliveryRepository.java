package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.EmailDelivery;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, Long> {
    boolean existsByEventId(String eventId);
    List<EmailDelivery> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
        String status,
        Instant time
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailDelivery e where e.id = :id")
    Optional<EmailDelivery> lockById(Long id);
}
