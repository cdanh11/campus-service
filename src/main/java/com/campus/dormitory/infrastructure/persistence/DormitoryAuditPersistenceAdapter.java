package com.campus.dormitory.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.dormitory.application.DormitoryAudit;
import com.campus.dormitory.domain.InventoryItem;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
class DormitoryAuditPersistenceAdapter implements DormitoryAudit {
    private final EntityManager entities;
    DormitoryAuditPersistenceAdapter(EntityManager entities) { this.entities = entities; }
    public void record(UUID actor, InventoryItem item, String action, Instant time) {
        try { entities.persist(new DormitoryAuditEntity(actor, item, action, time)); entities.flush(); }
        catch (DataAccessException | jakarta.persistence.PersistenceException failure) { throw new UnavailableException(failure); }
    }
}
