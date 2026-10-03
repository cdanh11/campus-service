package com.campus.academic.infrastructure.persistence.audit;

import java.time.Instant;
import java.util.UUID;
import com.campus.academic.application.AcademicAudit;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
class AcademicAuditPersistenceAdapter implements AcademicAudit {
    private final AcademicAuditJpaRepository events;
    AcademicAuditPersistenceAdapter(AcademicAuditJpaRepository events) { this.events = events; }
    public void record(UUID actorId, Resource resource, UUID targetId, Action action, long version, String status, Instant time) {
        try { events.saveAndFlush(new AcademicAuditEntity(actorId, resource, targetId, action, version, status, time)); }
        catch (DataAccessException exception) { throw new UnavailableException(exception); }
    }
}
