package com.campus.student.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Student(UUID id, String studentNumber, String fullName, String email, UUID identityUserId, UUID organizationUnitId,
                      StudentStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public Student {
        Objects.requireNonNull(id, "id");
        studentNumber = normalize(studentNumber, 32, "studentNumber").toUpperCase(java.util.Locale.ROOT);
        fullName = normalize(fullName, 160, "fullName");
        email = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (email != null && (email.length() > 320 || !email.contains("@"))) throw new InvalidStudentException();
        Objects.requireNonNull(organizationUnitId, "organizationUnitId"); Objects.requireNonNull(status, "status");
        if (rowVersion < 0) throw new InvalidStudentException(); Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
    }
    public static Student create(UUID id, String number, String name, String email, UUID identityUserId, UUID unitId, StudentStatus status, Instant now) { return new Student(id, number, name, email, identityUserId, unitId, status, 0, now, now); }
    private static String normalize(String value, int max, String field) { String result = Objects.requireNonNull(value, field).trim(); int length = result.codePointCount(0, result.length()); if (length < 2 || length > max) throw new InvalidStudentException(); return result; }
    public static final class InvalidStudentException extends IllegalArgumentException { }
}
