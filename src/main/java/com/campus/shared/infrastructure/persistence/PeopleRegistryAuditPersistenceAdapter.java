package com.campus.shared.infrastructure.persistence;
import java.time.Instant;
import java.util.UUID;
import com.campus.shared.application.PeopleRegistryAudit;
import org.springframework.stereotype.Repository;

@Repository
class PeopleRegistryAuditPersistenceAdapter implements PeopleRegistryAudit {
    private final PeopleRegistryAuditJpaRepository events;
    PeopleRegistryAuditPersistenceAdapter(PeopleRegistryAuditJpaRepository events) { this.events = events; }
    @Override public void record(UUID actorUserId, String resourceType, UUID targetId, String action, Instant occurredAt) { events.saveAndFlush(new PeopleRegistryAuditEntity(UUID.randomUUID(), actorUserId, resourceType, targetId, action, occurredAt)); }
}
