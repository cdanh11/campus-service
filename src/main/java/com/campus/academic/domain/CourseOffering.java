package com.campus.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record CourseOffering(UUID id, UUID termId, UUID courseId, UUID organizationUnitId, AcademicDeliveryStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public CourseOffering {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(termId, "termId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(organizationUnitId, "organizationUnitId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must be nonnegative");
    }
}
