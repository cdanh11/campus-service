package com.campus.library.infrastructure.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.campus.library.domain.LibrarySearch.Resource;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "library_audit_events")
public class LibraryAuditEntity {
    @Id UUID id;
    @Column(name = "actor_user_id", nullable = false) UUID actor;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 16) Resource resource;
    @Column(name = "target_id", nullable = false) UUID target;
    @Column(nullable = false, length = 16) String action;
    @Column(name = "resource_version", nullable = false) long version;
    @Column(name = "occurred_at", nullable = false) Instant time;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") Map<String, String> metadata;
    protected LibraryAuditEntity() { }
    LibraryAuditEntity(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time) {
        id = UUID.randomUUID(); this.actor = actor; this.resource = resource; this.target = target; this.action = action;
        this.version = version; this.time = time; metadata = Map.of("status", status);
    }
}
