package com.campus.library.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record BookLoan(UUID id, UUID copyId, UUID studentId, Status status, long rowVersion,
                       Instant borrowedAt, Instant dueAt, Instant returnedAt, Instant createdAt, Instant updatedAt) {
    public enum Status { OPEN, RETURNED }
    public BookLoan {
        Objects.requireNonNull(id); Objects.requireNonNull(copyId); Objects.requireNonNull(studentId);
        Objects.requireNonNull(status); Objects.requireNonNull(borrowedAt); Objects.requireNonNull(dueAt);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt); LibraryValues.version(rowVersion);
        if (!dueAt.isAfter(borrowedAt) || status == Status.OPEN && returnedAt != null
                || status == Status.RETURNED && (returnedAt == null || returnedAt.isBefore(borrowedAt)))
            throw new IllegalArgumentException("Invalid loan history");
    }
    public static BookLoan borrow(UUID id, UUID copyId, UUID studentId, Instant now) {
        return new BookLoan(id, copyId, studentId, Status.OPEN, 0, now, now.plus(14, ChronoUnit.DAYS), null, now, now);
    }
    public BookLoan returnBook(Instant now) {
        if (status != Status.OPEN) throw new IllegalStateException("Loan already returned");
        return new BookLoan(id, copyId, studentId, Status.RETURNED, rowVersion, borrowedAt, dueAt, now, createdAt, now);
    }
}
