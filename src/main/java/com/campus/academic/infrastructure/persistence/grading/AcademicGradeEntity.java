package com.campus.academic.infrastructure.persistence.grading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.campus.academic.domain.AcademicGrade;
import com.campus.academic.domain.GradeScheme;
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
@Table(name = "academic_grades")
@EntityListeners(AuditingEntityListener.class)
public class AcademicGradeEntity {
    @Id
    private UUID id;
    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GradeScheme scheme;
    @Column(name = "attendance_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal attendanceScore;
    @Column(name = "midterm_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal midtermScore;
    @Column(name = "final_score", precision = 4, scale = 2)
    private BigDecimal finalScore;
    @Column(name = "project_score", precision = 4, scale = 2)
    private BigDecimal projectScore;
    @Column(name = "total_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal totalScore;
    @Column(name = "letter_grade", nullable = false, length = 2)
    private String letterGrade;
    @Column(name = "grade_point", nullable = false, precision = 3, scale = 2)
    private BigDecimal gradePoint;
    @Column(nullable = false)
    private boolean passed;
    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicGradeEntity() { }

    public AcademicGradeEntity(AcademicGrade value) {
        id = value.id();
        enrollmentId = value.enrollmentId();
        recordedBy = value.recordedBy();
        createdAt = value.createdAt();
        apply(value);
    }

    public void apply(AcademicGrade value) {
        scheme = value.scheme();
        attendanceScore = decimal(value.attendanceScore());
        midtermScore = decimal(value.midtermScore());
        finalScore = value.finalScore() == null ? null : decimal(value.finalScore());
        projectScore = value.projectScore() == null ? null : decimal(value.projectScore());
        totalScore = decimal(value.totalScore());
        letterGrade = value.letterGrade();
        gradePoint = decimal(value.gradePoint());
        passed = value.passed();
        updatedAt = value.updatedAt();
    }

    public UUID getId() { return id; }
    public UUID getEnrollmentId() { return enrollmentId; }
    public GradeScheme getScheme() { return scheme; }
    public BigDecimal getAttendanceScore() { return attendanceScore; }
    public BigDecimal getMidtermScore() { return midtermScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public BigDecimal getProjectScore() { return projectScore; }
    public BigDecimal getTotalScore() { return totalScore; }
    public String getLetterGrade() { return letterGrade; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public boolean isPassed() { return passed; }
    public UUID getRecordedBy() { return recordedBy; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public AcademicGrade domain() {
        return new AcademicGrade(id, enrollmentId, scheme, attendanceScore.doubleValue(), midtermScore.doubleValue(),
                finalScore == null ? null : finalScore.doubleValue(), projectScore == null ? null : projectScore.doubleValue(),
                totalScore.doubleValue(), letterGrade, gradePoint.doubleValue(), passed, recordedBy, rowVersion,
                createdAt, updatedAt);
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }
}
