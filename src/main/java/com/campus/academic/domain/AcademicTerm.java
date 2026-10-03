package com.campus.academic.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record AcademicTerm(UUID id, String code, String name, LocalDate startDate, LocalDate endDate, AcademicTermStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public AcademicTerm {
        Objects.requireNonNull(id, "id");
        code = AcademicProgram.normalize(AcademicProgram.normalize(code, 32, "code").toUpperCase(Locale.ROOT), 32, "code");
        name = AcademicProgram.normalize(name, 160, "name");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must be nonnegative");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("term date order");
    }
}
