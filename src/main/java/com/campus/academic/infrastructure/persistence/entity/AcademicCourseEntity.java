package com.campus.academic.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;
import com.campus.academic.domain.AcademicCatalogStatus;
import com.campus.academic.domain.CourseGroup;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "academic_courses")
@EntityListeners(AuditingEntityListener.class)
public class AcademicCourseEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    private String code;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false)
    private int credits;
    @Enumerated(EnumType.STRING)
    @Column(name = "course_group", nullable = false, length = 20)
    private CourseGroup courseGroup;
    @Column(name = "has_final_project", nullable = false)
    private boolean hasFinalProject;
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

    protected AcademicCourseEntity() { }
    public AcademicCourseEntity(UUID id) { this.id = id; }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getCredits() { return credits; }
    public CourseGroup getCourseGroup() { return courseGroup; }
    public boolean isHasFinalProject() { return hasFinalProject; }
    public UUID getOrganizationUnitId() { return organizationUnitId; }
    public AcademicCatalogStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String code, String title, int credits, CourseGroup courseGroup, boolean hasFinalProject,
                       UUID organizationUnitId, AcademicCatalogStatus status) {
        this.code = code;
        this.title = title;
        this.credits = credits;
        this.courseGroup = courseGroup;
        this.hasFinalProject = hasFinalProject;
        this.organizationUnitId = organizationUnitId;
        this.status = status;
    }
}
