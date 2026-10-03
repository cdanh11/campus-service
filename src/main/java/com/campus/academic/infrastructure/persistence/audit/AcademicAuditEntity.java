package com.campus.academic.infrastructure.persistence.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.campus.academic.application.AcademicAudit;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "academic_audit_events")
public class AcademicAuditEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "actor_user_id", nullable = false) private UUID actorId;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 32) private AcademicAudit.Resource resource;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AcademicAudit.Action action;
    @Column(name = "resource_version", nullable = false) private long version;
    @Column(name = "occurred_at", nullable = false) private Instant time;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, String> metadata;

    protected AcademicAuditEntity() { }
    AcademicAuditEntity(UUID actorId, AcademicAudit.Resource resource, UUID targetId, AcademicAudit.Action action, long version, String status, Instant time) {
        this.id = UUID.randomUUID(); this.actorId = actorId; this.resource = resource; this.targetId = targetId;
        this.action = action; this.version = version; this.time = time; this.metadata = Map.of("status", status);
    }
}
