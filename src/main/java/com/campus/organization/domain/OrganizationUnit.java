package com.campus.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationUnit(UUID id, String code, String name, OrganizationUnitType unitType,
                               OrganizationUnitStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public OrganizationUnit {
        Objects.requireNonNull(id, "id must not be null");
        code = normalize(code, 32, "code");
        name = normalize(name, 160, "name");
        Objects.requireNonNull(unitType, "unitType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new InvalidOrganizationUnitException("rowVersion must not be negative");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static OrganizationUnit create(UUID id, String code, String name, OrganizationUnitType unitType, OrganizationUnitStatus status, Instant now) {
        return new OrganizationUnit(id, code, name, unitType, status, 0, now, now);
    }

    private static String normalize(String value, int maximumLength, String field) {
        String normalized = trimBoundaryWhitespace(Objects.requireNonNull(value, field + " must not be null"));
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 2 || length > maximumLength) throw new InvalidOrganizationUnitException(field + " length is invalid");
        return normalized;
    }

    private static String trimBoundaryWhitespace(String value) {
        int begin = 0;
        int end = value.length();
        while (begin < end && isBoundaryWhitespace(value.charAt(begin))) begin++;
        while (end > begin && isBoundaryWhitespace(value.charAt(end - 1))) end--;
        return value.substring(begin, end);
    }

    private static boolean isBoundaryWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\u000b' || value == '\f';
    }

    public static final class InvalidOrganizationUnitException extends IllegalArgumentException {
        public InvalidOrganizationUnitException(String message) { super(message); }
    }
}
