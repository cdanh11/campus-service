package com.campus.academic.infrastructure.persistence.delivery;

import java.util.Optional;
import java.util.UUID;
import com.campus.academic.domain.AcademicDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface AcademicTermJpaRepository extends JpaRepository<AcademicTermEntity, UUID>, JpaSpecificationExecutor<AcademicTermEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from AcademicTermEntity value where value.id = :id")
    Optional<AcademicTermEntity> findByIdForUpdate(UUID id);
}
