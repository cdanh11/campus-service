package com.campus.academic.domain;

import java.util.Set;
import java.util.UUID;

public record GradeSearch(int page, int size, UUID enrollmentId, String sortField, boolean ascending) {
    public GradeSearch {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || !Set.of("createdAt", "updatedAt", "totalScore").contains(sortField)) {
            throw new IllegalArgumentException("Invalid grade query");
        }
    }
}
