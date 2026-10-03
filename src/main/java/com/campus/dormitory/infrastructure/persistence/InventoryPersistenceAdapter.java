package com.campus.dormitory.infrastructure.persistence;

import java.util.*;
import com.campus.dormitory.application.DormitoryInventoryService;
import com.campus.dormitory.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class InventoryPersistenceAdapter implements InventoryRepository {
    private final EntityManager entities;
    InventoryPersistenceAdapter(EntityManager entities) { this.entities = entities; }

    private Class<? extends InventoryEntity> type(InventoryKind kind) {
        return switch (kind) { case BUILDING -> ResidenceBuildingEntity.class; case ROOM -> ResidenceRoomEntity.class; case BED -> ResidenceBedEntity.class; };
    }

    public Optional<InventoryItem> find(InventoryKind kind, UUID id) { return Optional.ofNullable(entities.find(type(kind), id)).map(InventoryEntity::domain); }
    public InventoryItem lock(InventoryKind kind, UUID id) {
        var entity = entities.find(type(kind), id);
        if (entity == null) throw new DormitoryInventoryService.NotFoundException();
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity.domain();
    }
    public InventoryItem create(InventoryItem item) {
        InventoryEntity entity = switch (item.kind()) {
            case BUILDING -> new ResidenceBuildingEntity(item);
            case ROOM -> new ResidenceRoomEntity(item);
            case BED -> new ResidenceBedEntity(item);
        };
        entities.persist(entity); entities.flush();
        return entity.domain();
    }
    public InventoryItem update(InventoryItem item, long expectedVersion) {
        var entity = entities.find(type(item.kind()), item.id());
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.version != expectedVersion) throw new DormitoryInventoryService.StaleVersionException();
        entity.update(item); entities.flush();
        return entity.domain();
    }
    public boolean hasActiveChildren(InventoryKind kind, UUID id) {
        if (kind == InventoryKind.BED) return false;
        String entity = kind == InventoryKind.BUILDING ? "ResidenceRoomEntity" : "ResidenceBedEntity";
        return entities.createQuery("select count(c) from " + entity + " c where c.parent = :parent and c.status = :status", Long.class)
                .setParameter("parent", id).setParameter("status", InventoryStatus.ACTIVE).getSingleResult() > 0;
    }
    public PageResult<InventoryItem> search(InventoryKind kind, InventorySearch query) { return search(type(kind), query); }
    private <T extends InventoryEntity> PageResult<InventoryItem> search(Class<T> type, InventorySearch query) {
        var builder = entities.getCriteriaBuilder();
        var select = builder.createQuery(type);
        var root = select.from(type);
        select.where(filters(builder, root, query));
        var field = root.get(query.sortField());
        select.orderBy(query.ascending() ? builder.asc(field) : builder.desc(field), builder.asc(root.get("id")));
        var rows = entities.createQuery(select).setFirstResult(query.page() * query.size()).setMaxResults(query.size()).getResultList();
        var count = builder.createQuery(Long.class);
        var countRoot = count.from(type);
        count.select(builder.count(countRoot)).where(filters(builder, countRoot, query));
        return new PageResult<>(rows.stream().map(InventoryEntity::domain).toList(), entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder builder, Root<?> root, InventorySearch query) {
        var filters = new ArrayList<Predicate>();
        if (query.status() != null) filters.add(builder.equal(root.get("status"), query.status()));
        if (query.parentId() != null) filters.add(builder.equal(root.get("parent"), query.parentId()));
        if (query.query() != null) {
            String text = "%" + query.query().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filters.add(builder.or(builder.like(builder.lower(root.get("code")), text, '!'), builder.like(builder.lower(root.get("name")), text, '!')));
        }
        return filters.toArray(Predicate[]::new);
    }
}
