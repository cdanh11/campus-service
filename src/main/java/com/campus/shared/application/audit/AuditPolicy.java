package com.campus.shared.application.audit;

import java.util.Map;
import java.util.Set;

/** Owner-declared supported resources and safe status values, not a new database constraint. */
public record AuditPolicy(int actionLength, Map<String, Set<String>> statuses) {
    public AuditPolicy {
        statuses = statuses.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
    public void validate(AuditSearch search) {
        if (search.resource() != null && !statuses.containsKey(search.resource())
                || search.action() != null && search.action().length() > actionLength)
            throw new IllegalArgumentException("Unsupported audit filter");
    }
    public Map<String, String> metadata(String resource, String status) {
        return status != null && statuses.getOrDefault(resource, Set.of()).contains(status) ? Map.of("status", status) : Map.of();
    }
}
