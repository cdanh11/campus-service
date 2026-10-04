package com.campus.shared.application.reporting;

import java.time.Instant;
import java.util.UUID;

/** Current-state records whose owner timestamp is in [from, until). */
public record ReportSearch(int page, int size, UUID studentId, UUID resourceId, String status,
                           Instant from, Instant until, boolean overdueOnly, boolean ascending) {
    public ReportSearch {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE
                || from!=null && until!=null && !from.isBefore(until))
            throw new IllegalArgumentException("Invalid report query");
    }
}
