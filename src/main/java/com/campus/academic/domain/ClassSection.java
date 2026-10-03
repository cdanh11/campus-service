package com.campus.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ClassSection(UUID id, UUID offeringId, String code, int capacity, UUID facultyId, AcademicDeliveryStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public ClassSection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(offeringId, "offeringId");
        code = normalizeCode(code);
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must be nonnegative");
        if (status == AcademicDeliveryStatus.OPEN && facultyId == null) throw new IllegalArgumentException("open section requires faculty");
    }
    public static String normalizeCode(String code) {
        return AcademicProgram.normalize(AcademicProgram.normalize(code, 32, "code").toUpperCase(Locale.ROOT), 32, "code");
    }
}
