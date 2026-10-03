package com.campus.dormitory.infrastructure.persistence;

import java.util.UUID;
import com.campus.dormitory.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "dormitory_buildings")
public class ResidenceBuildingEntity extends InventoryEntity {

    protected ResidenceBuildingEntity() { }
    ResidenceBuildingEntity(InventoryItem item) { initialize(item); }
    @Override protected InventoryKind kind() { return InventoryKind.BUILDING; }
    @Override protected UUID parentId() { return null; }
}
