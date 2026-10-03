package com.campus.academic.infrastructure.persistence.delivery;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface AcademicTermJpaRepository extends JpaRepository<AcademicTermEntity, UUID>, JpaSpecificationExecutor<AcademicTermEntity> {
}
