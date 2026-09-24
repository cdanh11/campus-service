package com.campus.shared.infrastructure.persistence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface PeopleRegistryAuditJpaRepository extends JpaRepository<PeopleRegistryAuditEntity, UUID> { }
