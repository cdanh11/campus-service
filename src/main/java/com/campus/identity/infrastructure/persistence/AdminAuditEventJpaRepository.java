package com.campus.identity.infrastructure.persistence;

import java.util.UUID;

import com.campus.identity.infrastructure.persistence.entity.AdminAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminAuditEventJpaRepository extends JpaRepository<AdminAuditEventEntity, UUID> {
}
