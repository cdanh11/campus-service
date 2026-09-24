package com.campus.organization.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "organization_units")
@EntityListeners(AuditingEntityListener.class)
public class OrganizationUnitEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "unit_type", nullable = false, length = 32) private OrganizationUnitType unitType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrganizationUnitStatus status;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected OrganizationUnitEntity() { }
    public OrganizationUnitEntity(UUID id) { this.id = id; }
    public UUID getId() { return id; } public String getCode() { return code; } public String getName() { return name; }
    public OrganizationUnitType getUnitType() { return unitType; } public OrganizationUnitStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
    public void update(String code, String name, OrganizationUnitType unitType, OrganizationUnitStatus status) { this.code = code; this.name = name; this.unitType = unitType; this.status = status; }
}
