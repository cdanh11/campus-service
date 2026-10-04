package com.campus.shared.application.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Only recorded fields; null version means the historical source never recorded it. */
public record AuditView(UUID id, AuditSource source, String resource, UUID targetId, UUID actorId,
                        String action, Long resourceVersion, Instant occurredAt, Map<String, String> metadata) {
    public AuditView { metadata = Map.copyOf(metadata); }
}
