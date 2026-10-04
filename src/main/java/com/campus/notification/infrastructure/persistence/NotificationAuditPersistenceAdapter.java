package com.campus.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.notification.application.NotificationAudit;
import jakarta.persistence.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
class NotificationAuditPersistenceAdapter implements NotificationAudit {
    private final EntityManager entities;
    NotificationAuditPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time) {
        try { entities.persist(new NotificationAuditEntity(actor,resource,target,action,version,status,time)); entities.flush(); }
        catch (DataAccessException | PersistenceException failure) { throw new UnavailableException(failure); }
    }
}
