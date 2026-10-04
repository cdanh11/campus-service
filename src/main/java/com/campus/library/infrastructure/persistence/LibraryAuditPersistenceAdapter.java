package com.campus.library.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.library.application.LibraryAudit;
import com.campus.library.domain.LibrarySearch.Resource;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
class LibraryAuditPersistenceAdapter implements LibraryAudit {
    private final EntityManager entities;
    LibraryAuditPersistenceAdapter(EntityManager entities) { this.entities = entities; }
    public void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time) {
        try {
            entities.persist(new LibraryAuditEntity(actor, resource, target, action, version, status, time)); entities.flush();
        } catch (RuntimeException failure) { throw new UnavailableException(failure); }
    }
}
