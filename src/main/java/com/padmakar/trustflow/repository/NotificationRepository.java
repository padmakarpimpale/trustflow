package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String username);

    boolean existsByEventId(String eventId);
}
