package com.campus.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdminAuditEvent(UUID id, UUID actorUserId, UUID targetUserId, AdminAuditAction action, Instant occurredAt, String metadata) {

    public AdminAuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        if (metadata.length() > 1000) {
            throw new IllegalArgumentException("metadata must not exceed 1000 characters");
        }
    }
}
