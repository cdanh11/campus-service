package com.campus.academic.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable membership identity; lifecycle policy is coordinated by the application. */
public record Enrollment(UUID id, UUID studentId, UUID sectionId, EnrollmentStatus status,
                         long rowVersion, Instant createdAt, Instant updatedAt) {
    public Enrollment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(sectionId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (rowVersion < 0) throw new IllegalArgumentException("Enrollment version must be nonnegative");
    }

    public Enrollment withStatus(EnrollmentStatus next, Instant now) {
        return new Enrollment(id, studentId, sectionId, next, rowVersion, createdAt, now);
    }
}
