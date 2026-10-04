package com.campus.shared.application.audit;

import java.time.Instant;
import java.util.UUID;

/** One owner source per request; time interval is [from, until). */
public record AuditSearch(int page, int size, UUID targetId, UUID actorId, String resource, String action,
                          Instant from, Instant until, boolean ascending) {
    public AuditSearch {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || from != null && until != null && !from.isBefore(until))
            throw new IllegalArgumentException("Invalid audit query");
        if (resource != null && !resource.matches("[A-Z_]{1,32}")
                || action != null && !action.matches("[A-Z_]{1,64}"))
            throw new IllegalArgumentException("Invalid audit filter");
    }
}
