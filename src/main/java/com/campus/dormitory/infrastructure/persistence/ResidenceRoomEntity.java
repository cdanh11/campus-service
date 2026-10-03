package com.campus.dormitory.infrastructure.persistence;

import java.util.UUID;
import com.campus.dormitory.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "dormitory_rooms")
public class ResidenceRoomEntity extends InventoryEntity {
    @Column(name = "building_id", nullable = false, updatable = false) private UUID parent;
    protected ResidenceRoomEntity() { }
    ResidenceRoomEntity(InventoryItem item) { parent = item.parentId(); initialize(item); }
    @Override protected InventoryKind kind() { return InventoryKind.ROOM; }
    @Override protected UUID parentId() { return parent; }
}
