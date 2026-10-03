package com.campus.dormitory.application;

import java.time.Instant;
import java.util.UUID;
import com.campus.dormitory.domain.InventoryItem;
import com.campus.dormitory.domain.AccommodationAssignment;

public interface DormitoryAudit {
    void record(UUID actor, InventoryItem item, String action, Instant time);
    void record(UUID actor, AccommodationAssignment assignment, String action, Instant time);
    enum Resource { BUILDING, ROOM, BED, ASSIGNMENT }
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super("Dormitory audit persistence failed", cause); }
    }
}
