package com.campus.dormitory.application;

import java.time.Clock;
import java.util.UUID;
import com.campus.dormitory.domain.*;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DormitoryInventoryService {
    private final InventoryRepository inventory;
    private final DormitoryAudit audit;
    private final Clock clock;

    public DormitoryInventoryService(InventoryRepository inventory, DormitoryAudit audit, Clock clock) {
        this.inventory = inventory;
        this.audit = audit;
        this.clock = clock;
    }

    public InventoryItem create(UUID actor, InventoryKind kind, UUID parent, String code, String name) {
        requireActor(actor);
        var now = clock.instant();
        var candidate = new InventoryItem(UUID.randomUUID(), kind, parent, code, name, InventoryStatus.ACTIVE, 0, now, now);
        lockAncestors(kind, parent, true);
        var saved = inventory.create(candidate);
        audit.record(actor, saved, "CREATED", now);
        return saved;
    }

    public InventoryItem update(UUID actor, InventoryKind kind, UUID id, String code, String name,
                                InventoryStatus status, long expectedVersion) {
        requireActor(actor);
        if (expectedVersion < 0) throw new IllegalArgumentException("Invalid expectedVersion");
        var known = get(kind, id);
        var candidate = new InventoryItem(id, kind, known.parentId(), code, name, status,
                known.rowVersion(), known.createdAt(), clock.instant());
        // Serialize child creation/activation and parent deactivation in building → room → bed order.
        lockAncestors(kind, known.parentId(), status == InventoryStatus.ACTIVE);
        var old = inventory.lock(kind, id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        if (status == InventoryStatus.INACTIVE && inventory.hasActiveChildren(kind, id)) throw new InvalidStateException();
        var saved = inventory.update(candidate, expectedVersion);
        audit.record(actor, saved, "UPDATED", clock.instant());
        return saved;
    }

    @Transactional(readOnly = true)
    public InventoryItem get(InventoryKind kind, UUID id) {
        return inventory.find(kind, id).orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResult<InventoryItem> search(InventoryKind kind, InventorySearch query) {
        if (kind == InventoryKind.BUILDING && query.parentId() != null) throw new InvalidQueryException();
        return inventory.search(kind, query);
    }

    private void lockAncestors(InventoryKind kind, UUID parent, boolean activeRequired) {
        if (kind == InventoryKind.BUILDING) return;
        InventoryItem building;
        InventoryItem room = null;
        if (kind == InventoryKind.BED) {
            var knownRoom = get(InventoryKind.ROOM, parent);
            building = inventory.lock(InventoryKind.BUILDING, knownRoom.parentId());
            room = inventory.lock(InventoryKind.ROOM, parent);
        } else {
            building = inventory.lock(InventoryKind.BUILDING, parent);
        }
        if (activeRequired && (building.status() != InventoryStatus.ACTIVE
                || room != null && room.status() != InventoryStatus.ACTIVE)) throw new ReferenceUnavailableException();
    }

    private void requireActor(UUID actor) {
        if (actor == null) throw new IllegalArgumentException("Authenticated actor required");
    }

    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
    public static final class ReferenceUnavailableException extends RuntimeException { }
    public static final class InvalidQueryException extends RuntimeException { }
}
