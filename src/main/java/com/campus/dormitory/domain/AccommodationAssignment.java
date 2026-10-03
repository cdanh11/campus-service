package com.campus.dormitory.domain;
import java.time.Instant;
import java.util.UUID;

public record AccommodationAssignment(UUID id, UUID studentId, UUID bedId, AssignmentStatus status, long rowVersion,
                                      Instant assignedAt, Instant releasedAt, Instant createdAt, Instant updatedAt) {
    public AccommodationAssignment {
        if (id == null || studentId == null || bedId == null || status == null || rowVersion < 0 || assignedAt == null
                || createdAt == null || updatedAt == null || (status == AssignmentStatus.ASSIGNED ? releasedAt != null : releasedAt == null)
                || releasedAt != null && releasedAt.isBefore(assignedAt)) throw new IllegalArgumentException("Invalid accommodation assignment");
    }
    public AccommodationAssignment release(Instant time) {
        if (status != AssignmentStatus.ASSIGNED) throw new IllegalStateException("Assignment already released");
        return new AccommodationAssignment(id, studentId, bedId, AssignmentStatus.RELEASED, rowVersion, assignedAt, time, createdAt, time);
    }
}
