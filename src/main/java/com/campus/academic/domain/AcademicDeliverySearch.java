package com.campus.academic.domain;

import java.util.UUID;

public record AcademicDeliverySearch(int page, int size, String query, String status,
                                     UUID termId, UUID courseId, UUID offeringId,
                                     String sortField, boolean ascending) {
    public AcademicDeliverySearch {
        query = query == null || query.trim().isEmpty() ? null : query.trim();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || query != null && query.codePointCount(0, query.length()) > 100) {
            throw new IllegalArgumentException("Invalid delivery query");
        }
    }
}
