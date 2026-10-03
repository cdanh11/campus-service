package com.campus.academic.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record AcademicCourse(UUID id, String code, String title, int credits, UUID organizationUnitId, AcademicCatalogStatus status,
                             long rowVersion, Instant createdAt, Instant updatedAt) {
    public AcademicCourse {
        Objects.requireNonNull(id, "id must not be null");
        code = AcademicProgram.normalize(AcademicProgram.normalize(code, 32, "code").toUpperCase(Locale.ROOT), 32, "code");
        title = AcademicProgram.normalize(title, 160, "title");
        if (credits < 1 || credits > 30) {
            throw new InvalidAcademicCourseException("credits must be between 1 and 30");
        }
        Objects.requireNonNull(organizationUnitId, "organizationUnitId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) {
            throw new InvalidAcademicCourseException("rowVersion must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static AcademicCourse create(UUID id, String code, String title, int credits, UUID organizationUnitId, AcademicCatalogStatus status, Instant now) {
        return new AcademicCourse(id, code, title, credits, organizationUnitId, status, 0, now, now);
    }

    public static final class InvalidAcademicCourseException extends IllegalArgumentException {
        public InvalidAcademicCourseException(String message) {
            super(message);
        }
    }
}
