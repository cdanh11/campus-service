package com.campus.dormitory.infrastructure.persistence;

import java.util.UUID;
import com.campus.dormitory.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "dormitory_beds")
public class ResidenceBedEntity extends InventoryEntity {
    @Column(name = "room_id", nullable = false, updatable = false) private UUID parent;
    protected ResidenceBedEntity() { }
    ResidenceBedEntity(InventoryItem item) { parent = item.parentId(); initialize(item); }
    @Override protected InventoryKind kind() { return InventoryKind.BED; }
    @Override protected UUID parentId() { return parent; }
}
