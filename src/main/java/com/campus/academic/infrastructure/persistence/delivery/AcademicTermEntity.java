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
@Table(name = "academic_terms")
@EntityListeners(AuditingEntityListener.class)
public class AcademicTermEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "code", nullable = false, length = 32)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademicTermStatus status;
    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AcademicTermEntity() { }
    public AcademicTermEntity(UUID id) { this.id = id; }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public AcademicTermStatus getStatus() { return status; }
    public long getRowVersion() { return rowVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(AcademicTerm value) {
        this.code = value.code();
        this.name = value.name();
        this.startDate = value.startDate();
        this.endDate = value.endDate();
        this.status = value.status();
    }
}
