package com.campus.student.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
@Table(name = "student_contact_profiles")
@EntityListeners(AuditingEntityListener.class)
public class StudentContactProfileEntity {
    @Id
    @Column(name = "student_id")
    private UUID studentId;
    @Column(length = 32)
    private String phone;
    @Column(length = 500)
    private String address;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(length = 8)
    private String cohort;
    @Column(name = "program_id")
    private UUID programId;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudentContactProfileEntity() {
    }

    public StudentContactProfileEntity(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getStudentId() { return studentId; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getCohort() { return cohort; }
    public UUID getProgramId() { return programId; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateContact(String phone, String address) {
        this.phone = phone;
        this.address = address;
    }

    public void updateAcademicProfile(String cohort, UUID programId) {
        this.cohort = cohort;
        this.programId = programId;
    }
}
