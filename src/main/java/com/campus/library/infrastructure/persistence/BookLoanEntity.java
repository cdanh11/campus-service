package com.campus.library.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.library.domain.BookLoan;
import jakarta.persistence.*;

@Entity @Table(name = "library_loans")
public class BookLoanEntity {
    @Id UUID id;
    @Column(name = "copy_id", nullable = false, updatable = false) UUID copyId;
    @Column(name = "student_id", nullable = false, updatable = false) UUID studentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) BookLoan.Status status;
    @Version @Column(name = "row_version", nullable = false) long version;
    @Column(name = "borrowed_at", nullable = false, updatable = false) Instant borrowedAt;
    @Column(name = "due_at", nullable = false, updatable = false) Instant dueAt;
    @Column(name = "returned_at") Instant returnedAt;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    protected BookLoanEntity() { }
    BookLoanEntity(BookLoan value) {
        id = value.id(); copyId = value.copyId(); studentId = value.studentId(); borrowedAt = value.borrowedAt();
        dueAt = value.dueAt(); createdAt = value.createdAt(); update(value);
    }
    void update(BookLoan value) { status = value.status(); returnedAt = value.returnedAt(); updatedAt = value.updatedAt(); }
    BookLoan domain() { return new BookLoan(id, copyId, studentId, status, version, borrowedAt, dueAt, returnedAt, createdAt, updatedAt); }
}
