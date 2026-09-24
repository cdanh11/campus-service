package com.campus.shared.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "people_registry_audit_events")
public class PeopleRegistryAuditEntity {
    @Id private UUID id;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "resource_type", nullable = false, length = 32) private String resourceType;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Column(nullable = false, length = 32) private String action;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 1000) private String metadata;
    protected PeopleRegistryAuditEntity() { }
    PeopleRegistryAuditEntity(UUID id, UUID actorUserId, String resourceType, UUID targetId, String action, Instant occurredAt) { this.id=id; this.actorUserId=actorUserId; this.resourceType=resourceType; this.targetId=targetId; this.action=action; this.occurredAt=occurredAt; this.metadata="{}"; }
}
