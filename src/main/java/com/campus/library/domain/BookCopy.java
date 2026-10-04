package com.campus.library.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A physical copy's title association is immutable; relabelling does not erase loan history. */
public record BookCopy(UUID id, UUID titleId, String code, LibraryStatus status,
                       long rowVersion, Instant createdAt, Instant updatedAt) {
    public BookCopy {
        Objects.requireNonNull(id); Objects.requireNonNull(titleId); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        code = LibraryValues.code(code); LibraryValues.version(rowVersion);
    }
    public static BookCopy create(UUID id, UUID titleId, String code, Instant now) {
        return new BookCopy(id, titleId, code, LibraryStatus.ACTIVE, 0, now, now);
    }
    public BookCopy update(String code, LibraryStatus status, Instant now) {
        return new BookCopy(id, titleId, code, status, rowVersion, createdAt, now);
    }
}
