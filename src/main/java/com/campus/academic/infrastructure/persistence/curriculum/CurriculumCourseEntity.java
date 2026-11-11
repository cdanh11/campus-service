package com.campus.academic.infrastructure.persistence.curriculum;

import java.time.Instant;
import java.util.UUID;
import com.campus.academic.domain.CurriculumCourse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "academic_curriculum_courses")
@EntityListeners(AuditingEntityListener.class)
public class CurriculumCourseEntity {
    @Id
    private UUID id;
    @Column(name = "program_id", nullable = false, updatable = false)
    private UUID programId;
    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;
    @Column(name = "semester_index", nullable = false)
    private int semesterIndex;
    @Column(name = "prerequisite_course_id")
    private UUID prerequisiteCourseId;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CurriculumCourseEntity() { }

    public CurriculumCourseEntity(CurriculumCourse value) {
        id = value.id();
        programId = value.programId();
        courseId = value.courseId();
        createdAt = value.createdAt();
        apply(value);
    }

    public void apply(CurriculumCourse value) {
        semesterIndex = value.semesterIndex();
        prerequisiteCourseId = value.prerequisiteCourseId();
        updatedAt = value.updatedAt();
    }

    public UUID getId() { return id; }
    public UUID getProgramId() { return programId; }
    public UUID getCourseId() { return courseId; }
    public int getSemesterIndex() { return semesterIndex; }
    public UUID getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public CurriculumCourse domain() {
        return new CurriculumCourse(id, programId, courseId, semesterIndex, prerequisiteCourseId, rowVersion,
                createdAt, updatedAt);
    }
}
