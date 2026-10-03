package com.campus.dormitory.infrastructure.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.campus.dormitory.domain.*;
import com.campus.dormitory.application.DormitoryAudit;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "dormitory_audit_events")
public class DormitoryAuditEntity {
    @Id @Column(nullable = false) private UUID id;
    @Column(name = "actor_user_id", nullable = false) private UUID actor;
    @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 16) private DormitoryAudit.Resource kind;
    @Column(name = "target_id", nullable = false) private UUID target;
    @Column(nullable = false, length = 16) private String action;
    @Column(name = "resource_version", nullable = false) private long version;
    @Column(name = "occurred_at", nullable = false) private Instant time;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, String> metadata;

    protected DormitoryAuditEntity() { }
    DormitoryAuditEntity(UUID actor, InventoryItem item, String action, Instant time) {
        id = UUID.randomUUID(); this.actor = actor; kind = DormitoryAudit.Resource.valueOf(item.kind().name()); target = item.id();
        this.action = action; version = item.rowVersion(); this.time = time; metadata = Map.of("status", item.status().name());
    }
    DormitoryAuditEntity(UUID actor, AccommodationAssignment item, String action, Instant time) {
        id = UUID.randomUUID(); this.actor = actor; kind = DormitoryAudit.Resource.ASSIGNMENT; target = item.id();
        this.action = action; version = item.rowVersion(); this.time = time; metadata = Map.of("status", item.status().name());
    }
}
