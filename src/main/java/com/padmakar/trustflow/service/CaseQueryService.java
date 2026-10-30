package com.padmakar.trustflow.service;

import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.repository.CaseRecordRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    private final CaseRecordRepository repository;
    private final CaseService cases;

    public CaseQueryService(CaseRecordRepository repository, CaseService cases) {
        this.repository = repository;
        this.cases = cases;
    }

    private Specification<CaseRecord> filter(
        String username,
        CaseStatus status,
        CaseType type,
        String q
    ) {
        boolean own = cases.user(username).getRole() == Role.SUBMITTER;
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (own) conditions.add(cb.equal(root.get("owner").get("username"), username));
            if (status != null) conditions.add(cb.equal(root.get("status"), status));
            if (type != null) conditions.add(cb.equal(root.get("type"), type));
            if (q != null && !q.isBlank()) {
                String escaped = q
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
                String term = "%" + escaped + "%";
                Predicate text = cb.or(
                    cb.like(cb.lower(root.get("title")), term, '!'),
                    cb.like(cb.lower(root.get("owner").get("username")), term, '!')
                );
                try {
                    text = cb.or(text, cb.equal(root.get("id"), Long.parseLong(q.trim())));
                } catch (NumberFormatException ignored) {
                    /* Text search only. */
                }
                conditions.add(text);
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    public Page<CaseRecord> search(
        String username,
        CaseStatus status,
        CaseType type,
        String q,
        int page,
        int size,
        String sort
    ) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.length() > 120)) {
            throw new IllegalArgumentException("Invalid page, size or search length");
        }
        if (!List.of("newest", "oldest", "amount").contains(sort)) {
            throw new IllegalArgumentException("Sort must be newest, oldest or amount");
        }
        Sort order = sort.equals("amount")
            ? Sort.by("amount").descending()
            : Sort.by(
                  sort.equals("oldest") ? Sort.Direction.ASC : Sort.Direction.DESC,
                  "createdAt"
              );
        return repository.findAll(
            filter(username, status, type, q),
            PageRequest.of(page, size, order.and(Sort.by("id").descending()))
        );
    }

    public Map<String, Long> summary(String username) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", repository.count(filter(username, null, null, null)));
        for (CaseStatus status : CaseStatus.values()) {
            counts.put(status.name(), repository.count(filter(username, status, null, null)));
        }
        return counts;
    }
}
