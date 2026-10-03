package com.campus.finance.infrastructure.obligations;

import java.time.Instant;
import java.util.UUID;
import com.campus.finance.application.FinanceAudit;
import jakarta.persistence.*;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
class FinanceAuditPersistenceAdapter implements FinanceAudit {
    private final EntityManager entities;
    FinanceAuditPersistenceAdapter(EntityManager entities) { this.entities = entities; }
    public void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time) {
        try { entities.persist(new FinanceAuditEntity(actor, resource, target, action, version, status, time)); entities.flush(); }
        catch (DataAccessException | PersistenceException failure) { throw new UnavailableException(failure); }
    }
}
