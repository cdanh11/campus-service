package com.campus.identity.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.campus.identity.domain.AdminAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_admin_audit_events")
public class AdminAuditEventEntity {
    @Id private UUID id;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "target_user_id", nullable = false) private UUID targetUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private AdminAuditAction action;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 1000) private String metadata;

    protected AdminAuditEventEntity() { }

    public AdminAuditEventEntity(UUID id, UUID actorUserId, UUID targetUserId, AdminAuditAction action, Instant occurredAt, String metadata) {
        this.id = id; this.actorUserId = actorUserId; this.targetUserId = targetUserId;
        this.action = action; this.occurredAt = occurredAt; this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getTargetUserId() { return targetUserId; }
    public AdminAuditAction getAction() { return action; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getMetadata() { return metadata; }
}
