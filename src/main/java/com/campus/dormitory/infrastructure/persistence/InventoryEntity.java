package com.campus.dormitory.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.dormitory.domain.*;
import jakarta.persistence.*;

@MappedSuperclass
public abstract class InventoryEntity {
    @Id @Column(nullable = false) protected UUID id;
    @Column(nullable = false, length = 32) protected String code;
    @Column(nullable = false, length = 160) protected String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) protected InventoryStatus status;
    @Version @Column(name = "row_version", nullable = false) protected long version;
    @Column(name = "created_at", nullable = false) protected Instant createdAt;
    @Column(name = "updated_at", nullable = false) protected Instant updatedAt;

    protected InventoryEntity() { }
    protected abstract InventoryKind kind();
    protected abstract UUID parentId();
    public InventoryItem domain() { return new InventoryItem(id, kind(), parentId(), code, name, status, version, createdAt, updatedAt); }
    void initialize(InventoryItem item) { id = item.id(); createdAt = item.createdAt(); update(item); }
    void update(InventoryItem item) { code = item.code(); name = item.name(); status = item.status(); updatedAt = item.updatedAt(); }
}
