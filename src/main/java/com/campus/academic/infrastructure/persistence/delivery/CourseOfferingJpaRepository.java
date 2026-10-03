package com.campus.academic.infrastructure.persistence.delivery;

import java.util.Optional;
import java.util.UUID;
import com.campus.academic.domain.AcademicDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

interface CourseOfferingJpaRepository extends JpaRepository<CourseOfferingEntity, UUID>, JpaSpecificationExecutor<CourseOfferingEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select value from CourseOfferingEntity value where value.id = :id")
    Optional<CourseOfferingEntity> findByIdForUpdate(UUID id);
    boolean existsByTermIdAndStatus(UUID termId, AcademicDeliveryStatus status);
}
