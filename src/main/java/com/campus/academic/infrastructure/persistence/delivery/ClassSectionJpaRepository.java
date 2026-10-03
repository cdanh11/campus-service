package com.campus.academic.infrastructure.persistence.delivery;

import java.util.Optional;
import java.util.UUID;
import com.campus.academic.domain.AcademicDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface ClassSectionJpaRepository extends JpaRepository<ClassSectionEntity, UUID>, JpaSpecificationExecutor<ClassSectionEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from ClassSectionEntity value where value.id = :id")
    Optional<ClassSectionEntity> findByIdForUpdate(UUID id);
    boolean existsByOfferingIdAndStatus(UUID offeringId, AcademicDeliveryStatus status);
}
