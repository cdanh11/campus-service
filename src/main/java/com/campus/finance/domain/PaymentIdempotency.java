package com.campus.finance.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentIdempotency(UUID actorUserId, String key, String requestHash, UUID paymentId, Instant createdAt) {
    public PaymentIdempotency {
        if (actorUserId == null || paymentId == null || createdAt == null || requestHash == null
                || !requestHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid payment idempotency");
        key = FinanceValues.idempotencyKey(key);
    }
}
