package com.campus.dormitory.infrastructure.assignment;

import java.util.*;
import com.campus.dormitory.application.DormitoryInventoryService;
import com.campus.dormitory.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class AssignmentPersistenceAdapter implements AssignmentRepository {
    private final EntityManager entities;
    AssignmentPersistenceAdapter(EntityManager entities) { this.entities = entities; }
    public Optional<AccommodationAssignment> find(UUID id) { return Optional.ofNullable(entities.find(AccommodationAssignmentEntity.class, id)).map(AccommodationAssignmentEntity::domain); }
    public AccommodationAssignment lock(UUID id) {
        var entity = entities.find(AccommodationAssignmentEntity.class, id);
        if (entity == null) throw new DormitoryInventoryService.NotFoundException();
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE); return entity.domain();
    }
    public AccommodationAssignment create(AccommodationAssignment value) {
        var entity = new AccommodationAssignmentEntity(value);
        entities.persist(entity); entities.flush();
        // Return stored timestamp precision, so POST and later GET/release retain identical history.
        entities.refresh(entity);
        return entity.domain();
    }
    public AccommodationAssignment update(AccommodationAssignment value, long expectedVersion) {
        var entity = entities.find(AccommodationAssignmentEntity.class, value.id());
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (entity.domain().rowVersion() != expectedVersion) throw new DormitoryInventoryService.StaleVersionException();
        entity.update(value); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public boolean hasCurrentBed(UUID id) { return current("bed", id); }
    public boolean hasCurrentStudent(UUID id) { return current("student", id); }
    private boolean current(String field, UUID id) {
        return entities.createQuery("select count(a) from AccommodationAssignmentEntity a where a." + field + " = :id and a.status = :status", Long.class)
                .setParameter("id", id).setParameter("status", AssignmentStatus.ASSIGNED).getSingleResult() > 0;
    }
    public PageResult<AccommodationAssignment> search(AssignmentSearch query) {
        var cb = entities.getCriteriaBuilder(); var select = cb.createQuery(AccommodationAssignmentEntity.class); var root = select.from(AccommodationAssignmentEntity.class);
        select.where(filters(cb, root, query)); var field = root.get(query.sortField());
        select.orderBy(query.ascending() ? cb.asc(field) : cb.desc(field), cb.asc(root.get("id")));
        var rows = entities.createQuery(select).setFirstResult(query.page() * query.size()).setMaxResults(query.size()).getResultList();
        var count = cb.createQuery(Long.class); var countRoot = count.from(AccommodationAssignmentEntity.class);
        count.select(cb.count(countRoot)).where(filters(cb, countRoot, query));
        return new PageResult<>(rows.stream().map(AccommodationAssignmentEntity::domain).toList(), entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb, Root<?> root, AssignmentSearch query) {
        var result = new ArrayList<Predicate>();
        if (query.studentId() != null) result.add(cb.equal(root.get("student"), query.studentId()));
        if (query.bedId() != null) result.add(cb.equal(root.get("bed"), query.bedId()));
        if (query.status() != null) result.add(cb.equal(root.get("status"), query.status()));
        return result.toArray(Predicate[]::new);
    }
}
