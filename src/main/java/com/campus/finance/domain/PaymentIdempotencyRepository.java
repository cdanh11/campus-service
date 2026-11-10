package com.campus.finance.domain;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIdempotencyRepository {
    void lock(UUID actorUserId, String key);
    Optional<PaymentIdempotency> find(UUID actorUserId, String key);
    PaymentIdempotency create(PaymentIdempotency value);
}
