package com.campus.academic.infrastructure.persistence.grading;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AcademicGradeJpaRepository extends JpaRepository<AcademicGradeEntity, UUID>, JpaSpecificationExecutor<AcademicGradeEntity> {
    Optional<AcademicGradeEntity> findByEnrollmentId(UUID enrollmentId);

    List<AcademicGradeEntity> findAllByEnrollmentIdIn(List<UUID> enrollmentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grade from AcademicGradeEntity grade where grade.id = :id")
    Optional<AcademicGradeEntity> findByIdForUpdate(UUID id);
}
