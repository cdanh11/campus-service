package com.campus.finance.infrastructure.obligations;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.campus.finance.application.FinanceAudit;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "finance_audit_events")
public class FinanceAuditEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "actor_user_id", nullable = false) private UUID actor;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 16) private FinanceAudit.Resource resource;
    @Column(name = "target_id", nullable = false) private UUID target;
    @Column(nullable = false, length = 16) private String action;
    @Column(name = "resource_version", nullable = false) private long version;
    @Column(name = "occurred_at", nullable = false) private Instant time;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, String> metadata;
    protected FinanceAuditEntity() { }
    FinanceAuditEntity(UUID actor, FinanceAudit.Resource resource, UUID target, String action, long version, String status, Instant time) {
        id = UUID.randomUUID(); this.actor = actor; this.resource = resource; this.target = target;
        this.action = action; this.version = version; this.time = time; metadata = Map.of("status", status);
    }
}
