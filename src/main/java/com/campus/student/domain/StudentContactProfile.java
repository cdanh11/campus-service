package com.campus.student.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Extra contact and study context kept apart from the registry row so students can maintain
 * their own phone and address without touching registry-owned identity fields.
 */
public record StudentContactProfile(UUID studentId, String phone, String address, LocalDate dateOfBirth,
                                    String cohort, UUID programId, long rowVersion, Instant createdAt, Instant updatedAt) {
    private static final Pattern COHORT = Pattern.compile("^K[0-9]{2}$");

    public StudentContactProfile {
        Objects.requireNonNull(studentId, "studentId");
        phone = normalizeOptional(phone, 32, "phone");
        address = normalizeOptional(address, 500, "address");
        if (phone != null && phone.length() < 8) throw new InvalidStudentContactException("phone");
        if (address != null && address.length() < 2) throw new InvalidStudentContactException("address");
        if (dateOfBirth != null && (dateOfBirth.isBefore(LocalDate.of(1900, 1, 1)) || dateOfBirth.isAfter(LocalDate.now()))) {
            throw new InvalidStudentContactException("dateOfBirth");
        }
        if (cohort != null) {
            cohort = cohort.trim().toUpperCase(Locale.ROOT);
            if (!COHORT.matcher(cohort).matches()) throw new InvalidStudentContactException("cohort");
        }
        if (rowVersion < 0) throw new InvalidStudentContactException("rowVersion");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static StudentContactProfile empty(UUID studentId, Instant now) {
        return new StudentContactProfile(studentId, null, null, null, null, null, 0, now, now);
    }

    public StudentContactProfile updateContact(String phone, String address, Instant now) {
        return new StudentContactProfile(studentId, phone, address, dateOfBirth, cohort, programId, rowVersion, createdAt, now);
    }

    public StudentContactProfile updateAcademicProfile(String cohort, UUID programId, Instant now) {
        return new StudentContactProfile(studentId, phone, address, dateOfBirth, cohort, programId, rowVersion, createdAt, now);
    }

    private static String normalizeOptional(String value, int maximum, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maximum) throw new InvalidStudentContactException(field);
        return normalized;
    }

    public static final class InvalidStudentContactException extends IllegalArgumentException {
        public InvalidStudentContactException(String field) {
            super(field);
        }
    }
}
