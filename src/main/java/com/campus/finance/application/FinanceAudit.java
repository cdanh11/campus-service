package com.campus.finance.application;

import java.time.Instant;
import java.util.UUID;

public interface FinanceAudit {
    enum Resource { FEE, CHARGE, PAYMENT }
    void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time);
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super(cause); }
    }
}
