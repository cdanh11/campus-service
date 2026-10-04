package com.campus.event.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.event.application.EventAudit;
import jakarta.persistence.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
class EventAuditPersistenceAdapter implements EventAudit {
    private final EntityManager entities;
    EventAuditPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public void record(UUID actor,Resource resource,UUID target,String action,long version,String status,Instant time) {
        try { entities.persist(new EventAuditEntity(actor,resource,target,action,version,status,time)); entities.flush(); }
        catch (DataAccessException | PersistenceException failure) { throw new UnavailableException(failure); }
    }
}
