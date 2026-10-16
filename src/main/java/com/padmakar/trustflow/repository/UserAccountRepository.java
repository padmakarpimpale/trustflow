package com.padmakar.trustflow.repository;

import com.padmakar.trustflow.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findByRole(Role role);
}
