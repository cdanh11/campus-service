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
@Table(name = "academic_class_sections")
@EntityListeners(AuditingEntityListener.class)
public class ClassSectionEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "offering_id", nullable = false, updatable = false)
    private UUID offeringId;
    @Column(name = "code", nullable = false, length = 32)
    private String code;
    @Column(name = "capacity", nullable = false)
    private int capacity;
    @Column(name = "faculty_id", nullable = true)
    private UUID facultyId;
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

    protected ClassSectionEntity() { }
    public ClassSectionEntity(UUID id) { this.id = id; }
    public UUID getId() { return id; }
    public UUID getOfferingId() { return offeringId; }
    public String getCode() { return code; }
    public int getCapacity() { return capacity; }
    public UUID getFacultyId() { return facultyId; }
    public AcademicDeliveryStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(ClassSection value) {
        this.offeringId = value.offeringId();
        this.code = value.code();
        this.capacity = value.capacity();
        this.facultyId = value.facultyId();
        this.status = value.status();
    }
}
