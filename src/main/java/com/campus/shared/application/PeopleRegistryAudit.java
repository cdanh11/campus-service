package com.campus.shared.application;

import java.time.Instant;
import java.util.UUID;

public interface PeopleRegistryAudit {
    void record(UUID actorUserId, String resourceType, UUID targetId, String action, Instant occurredAt);
}
