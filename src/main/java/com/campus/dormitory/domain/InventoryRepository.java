package com.campus.dormitory.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface InventoryRepository {
    Optional<InventoryItem> find(InventoryKind kind, UUID id);
    List<InventoryItem> findAll(InventoryKind kind, Collection<UUID> ids);
    InventoryItem lock(InventoryKind kind, UUID id);
    InventoryItem create(InventoryItem item);
    InventoryItem update(InventoryItem item, long expectedVersion);
    boolean hasActiveChildren(InventoryKind parentKind, UUID id);
    PageResult<InventoryItem> search(InventoryKind kind, InventorySearch query);
}
