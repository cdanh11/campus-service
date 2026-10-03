package com.campus.dormitory.infrastructure.assignment;

import java.time.Instant;
import java.util.UUID;
import com.campus.dormitory.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "dormitory_assignments")
public class AccommodationAssignmentEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "student_id", nullable = false, updatable = false) private UUID student;
    @Column(name = "bed_id", nullable = false, updatable = false) private UUID bed;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AssignmentStatus status;
    @Version @Column(name = "row_version", nullable = false) private long version;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt;
    @Column(name = "released_at") private Instant releasedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AccommodationAssignmentEntity() { }
    AccommodationAssignmentEntity(AccommodationAssignment value) {
        id = value.id(); student = value.studentId(); bed = value.bedId(); assignedAt = value.assignedAt(); createdAt = value.createdAt(); update(value);
    }
    void update(AccommodationAssignment value) { status = value.status(); releasedAt = value.releasedAt(); updatedAt = value.updatedAt(); }
    AccommodationAssignment domain() { return new AccommodationAssignment(id, student, bed, status, version, assignedAt, releasedAt, createdAt, updatedAt); }
}
