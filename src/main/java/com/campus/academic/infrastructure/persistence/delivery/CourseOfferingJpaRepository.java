package com.campus.academic.infrastructure.persistence.delivery;

import java.util.UUID;
import com.campus.academic.domain.AcademicDeliveryStatus;
import org.springframework.data.jpa.repository.*;

interface CourseOfferingJpaRepository extends JpaRepository<CourseOfferingEntity, UUID>, JpaSpecificationExecutor<CourseOfferingEntity> {
    boolean existsByTermIdAndStatus(UUID termId, AcademicDeliveryStatus status);
}
