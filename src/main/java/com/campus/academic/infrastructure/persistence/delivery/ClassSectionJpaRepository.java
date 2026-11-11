package com.campus.academic.infrastructure.persistence.delivery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import com.campus.academic.domain.AcademicDeliveryStatus;
import org.springframework.data.jpa.repository.*;

interface ClassSectionJpaRepository extends JpaRepository<ClassSectionEntity, UUID>, JpaSpecificationExecutor<ClassSectionEntity> {
    boolean existsByOfferingIdAndStatus(UUID offeringId, AcademicDeliveryStatus status);
    List<ClassSectionEntity> findAllByOfferingIdInAndStatus(Collection<UUID> offeringIds, AcademicDeliveryStatus status);
}
