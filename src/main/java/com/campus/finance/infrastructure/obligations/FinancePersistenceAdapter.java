package com.campus.finance.infrastructure.obligations;

import java.util.*;
import java.util.function.Function;
import com.campus.finance.application.FinanceObligationService;
import com.campus.finance.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class FinancePersistenceAdapter implements FinanceRepository {
    private final EntityManager entities;
    FinancePersistenceAdapter(EntityManager entities) { this.entities = entities; }
    public Optional<FeeDefinition> fee(UUID id) { return Optional.ofNullable(entities.find(FeeDefinitionEntity.class, id)).map(FeeDefinitionEntity::domain); }
    public FeeDefinition lockFee(UUID id) { return lock(FeeDefinitionEntity.class, id).domain(); }
    public FeeDefinition createFee(FeeDefinition value) {
        var entity = new FeeDefinitionEntity(value); entities.persist(entity); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public FeeDefinition updateFee(FeeDefinition value, long expectedVersion) {
        var entity = lock(FeeDefinitionEntity.class, value.id());
        if (entity.domain().rowVersion() != expectedVersion) throw new FinanceObligationService.StaleVersionException();
        entity.update(value); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public Optional<StudentCharge> charge(UUID id) { return Optional.ofNullable(entities.find(StudentChargeEntity.class, id)).map(StudentChargeEntity::domain); }
    public StudentCharge lockCharge(UUID id) { return lock(StudentChargeEntity.class, id).domain(); }
    public StudentCharge createCharge(StudentCharge value) {
        var entity = new StudentChargeEntity(value); entities.persist(entity); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public StudentCharge updateCharge(StudentCharge value, long expectedVersion) {
        var entity = lock(StudentChargeEntity.class, value.id());
        if (entity.domain().rowVersion() != expectedVersion) throw new FinanceObligationService.StaleVersionException();
        entity.update(value); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    private <T> T lock(Class<T> type, UUID id) {
        T entity = entities.find(type, id);
        if (entity == null) throw new FinanceObligationService.NotFoundException();
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE); return entity;
    }
    public StudentCharge advanceChargeVersion(UUID id, long expectedVersion, java.time.Instant time) {
        var entity = lock(StudentChargeEntity.class, id);
        int changed = entities.createQuery("update StudentChargeEntity c set c.version=c.version+1,c.updatedAt=:time where c.id=:id and c.version=:version")
                .setParameter("id",id).setParameter("version",expectedVersion).setParameter("time",time).executeUpdate();
        if(changed != 1) throw new FinanceObligationService.StaleVersionException();
        entities.refresh(entity); return entity.domain();
    }
    public PageResult<FeeDefinition> fees(FinanceSearch query) { query.fees(); return search(FeeDefinitionEntity.class, query, false, FeeDefinitionEntity::domain); }
    public PageResult<StudentCharge> charges(FinanceSearch query) { query.charges(); return search(StudentChargeEntity.class, query, true, StudentChargeEntity::domain); }
    private <T, D> PageResult<D> search(Class<T> type, FinanceSearch query, boolean charge, Function<T, D> domain) {
        var cb = entities.getCriteriaBuilder(); var select = cb.createQuery(type); var root = select.from(type);
        select.where(filters(cb, root, query, charge)); var field = root.get(query.sortField());
        select.orderBy(query.ascending() ? cb.asc(field) : cb.desc(field), cb.asc(root.get("id")));
        var rows = entities.createQuery(select).setFirstResult(query.page() * query.size()).setMaxResults(query.size()).getResultList();
        var count = cb.createQuery(Long.class); var countRoot = count.from(type);
        count.select(cb.count(countRoot)).where(filters(cb, countRoot, query, charge));
        return new PageResult<>(rows.stream().map(domain).toList(), entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb, Root<?> root, FinanceSearch query, boolean charge) {
        var filters = new ArrayList<Predicate>();
        if (query.status() != null) filters.add(cb.equal(root.get("status"), charge ? ChargeStatus.valueOf(query.status()) : FeeStatus.valueOf(query.status())));
        if (charge && query.studentId() != null) filters.add(cb.equal(root.get("studentId"), query.studentId()));
        if (charge && query.feeId() != null) filters.add(cb.equal(root.get("feeId"), query.feeId()));
        if (query.query() != null) {
            String text = "%" + query.query().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filters.add(cb.or(cb.like(cb.lower(root.get(charge ? "chargeNumber" : "code")), text, '!'),
                    cb.like(cb.lower(root.get(charge ? "feeName" : "name")), text, '!')));
        }
        return filters.toArray(Predicate[]::new);
    }
}
