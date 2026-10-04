package com.campus.library.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.library.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "library_copies")
public class BookCopyEntity {
    @Id UUID id;
    @Column(name = "title_id", nullable = false, updatable = false) UUID titleId;
    @Column(nullable = false, length = 32) String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) LibraryStatus status;
    @Version @Column(name = "row_version", nullable = false) long version;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected BookCopyEntity() { }
    BookCopyEntity(BookCopy value) { id = value.id(); titleId = value.titleId(); createdAt = value.createdAt(); update(value); }
    void update(BookCopy value) { code = value.code(); status = value.status(); updatedAt = value.updatedAt(); }
    BookCopy domain() { return new BookCopy(id, titleId, code, status, version, createdAt, updatedAt); }
}
