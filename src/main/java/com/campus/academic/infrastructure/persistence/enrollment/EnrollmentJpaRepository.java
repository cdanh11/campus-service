package com.campus.academic.infrastructure.persistence.enrollment;

import java.util.UUID;
import com.campus.academic.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface EnrollmentJpaRepository extends JpaRepository<EnrollmentEntity, UUID>, JpaSpecificationExecutor<EnrollmentEntity> {
    boolean existsByStudentIdAndSectionId(UUID studentId, UUID sectionId);

    @org.springframework.data.jpa.repository.Query("""
            select count(enrollment) from EnrollmentEntity enrollment
            where enrollment.studentId = :studentId
              and enrollment.status = com.campus.academic.domain.EnrollmentStatus.ENROLLED
              and enrollment.sectionId in (
                  select section.id from ClassSectionEntity section where section.offeringId = :offeringId)
            """)
    long countActiveByStudentIdAndOfferingId(
            @org.springframework.data.repository.query.Param("studentId") UUID studentId,
            @org.springframework.data.repository.query.Param("offeringId") UUID offeringId);

    long countBySectionIdAndStatus(UUID sectionId, EnrollmentStatus status);
}
