package com.campus.academic.infrastructure.persistence.delivery;

import java.util.List;
import java.util.UUID;
import com.campus.academic.domain.AcademicTermStatus;
import org.springframework.data.jpa.repository.*;

interface AcademicTermJpaRepository extends JpaRepository<AcademicTermEntity, UUID>, JpaSpecificationExecutor<AcademicTermEntity> {
    List<AcademicTermEntity> findAllByStatus(AcademicTermStatus status);
}
