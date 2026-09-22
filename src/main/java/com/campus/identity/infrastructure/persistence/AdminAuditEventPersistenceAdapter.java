package com.campus.identity.infrastructure.persistence;

import com.campus.identity.domain.AdminAuditEvent;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.infrastructure.persistence.entity.AdminAuditEventEntity;
import org.springframework.stereotype.Repository;

@Repository
class AdminAuditEventPersistenceAdapter implements AdminAuditEventRepository {
    private final AdminAuditEventJpaRepository repository;

    AdminAuditEventPersistenceAdapter(AdminAuditEventJpaRepository repository) { this.repository = repository; }

    @Override
    public AdminAuditEvent save(AdminAuditEvent event) {
        AdminAuditEventEntity saved = repository.save(new AdminAuditEventEntity(event.id(), event.actorUserId(), event.targetUserId(),
                event.action(), event.occurredAt(), event.metadata()));
        return new AdminAuditEvent(saved.getId(), saved.getActorUserId(), saved.getTargetUserId(), saved.getAction(), saved.getOccurredAt(), saved.getMetadata());
    }
}
