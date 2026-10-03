package com.campus.academic.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import com.campus.academic.infrastructure.persistence.entity.AcademicCourseEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface AcademicCourseJpaRepository extends JpaRepository<AcademicCourseEntity, UUID>, JpaSpecificationExecutor<AcademicCourseEntity> {
    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from AcademicCourseEntity value where value.id = :id")
    Optional<AcademicCourseEntity> findByIdForUpdate(UUID id);
}
