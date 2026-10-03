package com.campus.academic.domain;

import java.util.Set;
import java.util.UUID;

public record EnrollmentSearch(int page, int size, UUID studentId, UUID sectionId,
                               EnrollmentStatus status, String sortField, boolean ascending) {
    public EnrollmentSearch {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || sortField == null || !Set.of("status", "createdAt", "updatedAt").contains(sortField)) {
            throw new IllegalArgumentException("Invalid enrollment query");
        }
    }
}
