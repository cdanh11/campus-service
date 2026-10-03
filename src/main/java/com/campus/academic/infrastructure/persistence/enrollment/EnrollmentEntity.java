package com.campus.academic.infrastructure.persistence.enrollment;

import java.time.Instant;
import java.util.UUID;
import com.campus.academic.domain.Enrollment;
import com.campus.academic.domain.EnrollmentStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "academic_enrollments")
public class EnrollmentEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "student_id", nullable = false, updatable = false) private UUID studentId;
    @Column(name = "section_id", nullable = false, updatable = false) private UUID sectionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EnrollmentStatus status;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EnrollmentEntity() { }
    EnrollmentEntity(Enrollment value) {
        id = value.id(); studentId = value.studentId(); sectionId = value.sectionId();
        createdAt = value.createdAt(); update(value);
    }
    void update(Enrollment value) { status = value.status(); updatedAt = value.updatedAt(); }
    Enrollment domain() { return new Enrollment(id, studentId, sectionId, status, rowVersion, createdAt, updatedAt); }
}
