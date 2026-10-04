package com.campus.library.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BookTitle(UUID id, String code, String title, String author, LibraryStatus status,
                        long rowVersion, Instant createdAt, Instant updatedAt) {
    public BookTitle {
        Objects.requireNonNull(id); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        code = LibraryValues.code(code); title = LibraryValues.text(title, 160); author = LibraryValues.text(author, 160);
        LibraryValues.version(rowVersion);
    }
    public static BookTitle create(UUID id, String code, String title, String author, Instant now) {
        return new BookTitle(id, code, title, author, LibraryStatus.ACTIVE, 0, now, now);
    }
    public BookTitle update(String code, String title, String author, LibraryStatus status, Instant now) {
        return new BookTitle(id, code, title, author, status, rowVersion, createdAt, now);
    }
}
