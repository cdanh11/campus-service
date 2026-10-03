package com.campus.academic.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;
import com.campus.academic.domain.AcademicCatalogStatus;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "academic_programs")
@EntityListeners(AuditingEntityListener.class)
public class AcademicProgramEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "organization_unit_id", nullable = false)
    private UUID organizationUnitId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcademicCatalogStatus status;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicProgramEntity() { }
    public AcademicProgramEntity(UUID id) { this.id = id; }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getOrganizationUnitId() { return organizationUnitId; }
    public AcademicCatalogStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String code, String name, UUID organizationUnitId, AcademicCatalogStatus status) {
        this.code = code;
        this.name = name;
        this.organizationUnitId = organizationUnitId;
        this.status = status;
    }
}
