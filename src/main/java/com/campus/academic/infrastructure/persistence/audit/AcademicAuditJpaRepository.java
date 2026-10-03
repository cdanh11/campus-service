package com.campus.academic.infrastructure.persistence.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AcademicAuditJpaRepository extends JpaRepository<AcademicAuditEntity, UUID> { }
