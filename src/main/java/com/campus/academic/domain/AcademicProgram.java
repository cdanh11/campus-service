package com.campus.academic.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record AcademicProgram(UUID id, String code, String name, UUID organizationUnitId, AcademicCatalogStatus status,
                              long rowVersion, Instant createdAt, Instant updatedAt) {
    public AcademicProgram {
        Objects.requireNonNull(id, "id must not be null");
        code = normalize(normalize(code, 32, "code").toUpperCase(Locale.ROOT), 32, "code");
        name = normalize(name, 160, "name");
        Objects.requireNonNull(organizationUnitId, "organizationUnitId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) {
            throw new InvalidAcademicProgramException("rowVersion must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static AcademicProgram create(UUID id, String code, String name, UUID organizationUnitId, AcademicCatalogStatus status, Instant now) {
        return new AcademicProgram(id, code, name, organizationUnitId, status, 0, now, now);
    }

    static String normalize(String value, int maximumLength, String field) {
        String normalized = value == null ? null : trimBoundaryWhitespace(value);
        if (normalized == null || normalized.codePointCount(0, normalized.length()) < 2
                || normalized.codePointCount(0, normalized.length()) > maximumLength) {
            throw new InvalidAcademicProgramException(field + " length is invalid");
        }
        return normalized;
    }

    private static String trimBoundaryWhitespace(String value) {
        int begin = 0, end = value.length();
        while (begin < end && isBoundaryWhitespace(value.charAt(begin))) {
            begin++;
        }
        while (end > begin && isBoundaryWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(begin, end);
    }

    private static boolean isBoundaryWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u000b' || value == '\f';
    }

    public static final class InvalidAcademicProgramException extends IllegalArgumentException {
        public InvalidAcademicProgramException(String message) {
            super(message);
        }
    }
}
