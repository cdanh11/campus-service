package com.campus.dormitory.domain;
import java.util.Set;
import java.util.UUID;

public record AssignmentSearch(int page, int size, UUID studentId, UUID bedId, AssignmentStatus status, String sortField, boolean ascending) {
    public AssignmentSearch {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || sortField == null
                || !Set.of("assignedAt", "createdAt", "updatedAt", "status").contains(sortField)) throw new IllegalArgumentException("Invalid assignment query");
    }
}
