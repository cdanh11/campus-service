package com.campus.academic.infrastructure.persistence.delivery;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.campus.academic.domain.*;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "academic_course_offerings")
@EntityListeners(AuditingEntityListener.class)
public class CourseOfferingEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "term_id", nullable = false, updatable = false)
    private UUID termId;
    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;
    @Column(name = "organization_unit_id", nullable = false, updatable = false)
    private UUID organizationUnitId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademicDeliveryStatus status;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseOfferingEntity() { }
    public CourseOfferingEntity(UUID id) { this.id = id; }
    public UUID getId() { return id; }
    public UUID getTermId() { return termId; }
    public UUID getCourseId() { return courseId; }
    public UUID getOrganizationUnitId() { return organizationUnitId; }
    public AcademicDeliveryStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(CourseOffering value) {
        this.termId = value.termId();
        this.courseId = value.courseId();
        this.organizationUnitId = value.organizationUnitId();
        this.status = value.status();
    }
}
