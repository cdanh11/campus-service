package com.campus.dormitory.application;

import java.time.Instant;
import java.util.UUID;
import com.campus.dormitory.domain.InventoryItem;

public interface DormitoryAudit {
    void record(UUID actor, InventoryItem item, String action, Instant time);
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super("Dormitory audit persistence failed", cause); }
    }
}
