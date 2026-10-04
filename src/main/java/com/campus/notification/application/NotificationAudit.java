package com.campus.notification.application;

import java.time.Instant;
import java.util.UUID;

public interface NotificationAudit {
    enum Resource { TEMPLATE, NOTICE, DELIVERY }
    void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time);
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super(cause); }
    }
}
