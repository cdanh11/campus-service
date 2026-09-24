package com.campus.student.infrastructure.persistence.entity;
import java.time.Instant; import java.util.UUID;
import com.campus.student.domain.StudentStatus; import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate; import org.springframework.data.annotation.LastModifiedDate; import org.springframework.data.jpa.domain.support.AuditingEntityListener;
@Entity @Table(name="students") @EntityListeners(AuditingEntityListener.class)
public class StudentEntity {
 @Id private UUID id; @Column(name="student_number",nullable=false,length=32) private String studentNumber; @Column(name="full_name",nullable=false,length=160) private String fullName; @Column(length=320) private String email; @Column(name="organization_unit_id",nullable=false) private UUID organizationUnitId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private StudentStatus status; @Version @Column(name="row_version",nullable=false) private long rowVersion; @CreatedDate @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @LastModifiedDate @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected StudentEntity(){} public StudentEntity(UUID id){this.id=id;} public UUID getId(){return id;} public String getStudentNumber(){return studentNumber;} public String getFullName(){return fullName;} public String getEmail(){return email;} public UUID getOrganizationUnitId(){return organizationUnitId;} public StudentStatus getStatus(){return status;} public long getRowVersion(){return rowVersion;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
 public void update(String n,String f,String e,UUID u,StudentStatus s){studentNumber=n;fullName=f;email=e;organizationUnitId=u;status=s;}
}
