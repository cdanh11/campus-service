package com.campus.academic.infrastructure.persistence.enrollment;

import java.util.UUID;
import com.campus.academic.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface EnrollmentJpaRepository extends JpaRepository<EnrollmentEntity, UUID>, JpaSpecificationExecutor<EnrollmentEntity> {
    boolean existsByStudentIdAndSectionId(UUID studentId, UUID sectionId);
    long countBySectionIdAndStatus(UUID sectionId, EnrollmentStatus status);
}
