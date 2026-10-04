package com.campus.library.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.library.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "library_titles")
public class BookTitleEntity {
    @Id UUID id;
    @Column(nullable = false, length = 32) String code;
    @Column(nullable = false, length = 160) String title;
    @Column(nullable = false, length = 160) String author;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) LibraryStatus status;
    @Version @Column(name = "row_version", nullable = false) long version;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected BookTitleEntity() { }
    BookTitleEntity(BookTitle value) { id = value.id(); createdAt = value.createdAt(); update(value); }
    void update(BookTitle value) {
        code = value.code(); title = value.title(); author = value.author(); status = value.status(); updatedAt = value.updatedAt();
    }
    BookTitle domain() { return new BookTitle(id, code, title, author, status, version, createdAt, updatedAt); }
}
