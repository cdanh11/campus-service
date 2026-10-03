package com.campus.academic.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import com.campus.academic.infrastructure.persistence.entity.AcademicProgramEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface AcademicProgramJpaRepository extends JpaRepository<AcademicProgramEntity, UUID>, JpaSpecificationExecutor<AcademicProgramEntity> {
    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from AcademicProgramEntity value where value.id = :id")
    Optional<AcademicProgramEntity> findByIdForUpdate(UUID id);
}
