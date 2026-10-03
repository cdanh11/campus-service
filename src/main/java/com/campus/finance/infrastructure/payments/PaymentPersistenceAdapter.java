package com.campus.finance.infrastructure.payments;

import java.math.BigDecimal;
import java.util.*;
import com.campus.finance.application.FinanceObligationService;
import com.campus.finance.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class PaymentPersistenceAdapter implements PaymentRepository {
    private final EntityManager entities;
    PaymentPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public Optional<ManualPayment> find(UUID id) { return Optional.ofNullable(entities.find(ManualPaymentEntity.class,id)).map(ManualPaymentEntity::domain); }
    public ManualPayment lock(UUID id) {
        var value=entities.find(ManualPaymentEntity.class,id);
        if(value==null) throw new FinanceObligationService.NotFoundException();
        entities.refresh(value,LockModeType.PESSIMISTIC_WRITE); return value.domain();
    }
    public ManualPayment create(ManualPayment value) {
        var entity=new ManualPaymentEntity(value); entities.persist(entity); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public ManualPayment update(ManualPayment value,long expectedVersion) {
        lock(value.id()); var entity=entities.find(ManualPaymentEntity.class,value.id());
        if(entity.domain().rowVersion()!=expectedVersion) throw new FinanceObligationService.StaleVersionException();
        entity.update(value); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public BigDecimal totalRecorded(UUID id) {
        return entities.createQuery("select coalesce(sum(p.amount),0) from ManualPaymentEntity p where p.chargeId=:id and p.status=:status",BigDecimal.class)
                .setParameter("id",id).setParameter("status",PaymentStatus.RECORDED).getSingleResult();
    }
    public Optional<ChargeBalance> balance(UUID id) {
        return entities.createQuery("""
                select new com.campus.finance.domain.ChargeBalance(c.id,c.amount,c.currency,c.status,c.version,coalesce(sum(p.amount),0))
                from StudentChargeEntity c left join ManualPaymentEntity p on p.chargeId=c.id and p.status=:status
                where c.id=:id group by c.id,c.amount,c.currency,c.status,c.version
                """,ChargeBalance.class).setParameter("id",id).setParameter("status",PaymentStatus.RECORDED).getResultStream().findFirst();
    }
    public PageResult<ManualPayment> search(PaymentSearch query) {
        var cb=entities.getCriteriaBuilder(); var select=cb.createQuery(ManualPaymentEntity.class); var root=select.from(ManualPaymentEntity.class);
        select.where(filters(cb,root,query)); var field=root.get(query.sortField());
        select.orderBy(query.ascending()?cb.asc(field):cb.desc(field),cb.asc(root.get("id")));
        var rows=entities.createQuery(select).setFirstResult(query.page()*query.size()).setMaxResults(query.size()).getResultList();
        var count=cb.createQuery(Long.class); var countRoot=count.from(ManualPaymentEntity.class);
        count.select(cb.count(countRoot)).where(filters(cb,countRoot,query));
        return new PageResult<>(rows.stream().map(ManualPaymentEntity::domain).toList(),entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb,Root<?> root,PaymentSearch query) {
        var result=new ArrayList<Predicate>();
        if(query.chargeId()!=null) result.add(cb.equal(root.get("chargeId"),query.chargeId()));
        if(query.status()!=null) result.add(cb.equal(root.get("status"),query.status()));
        if(query.query()!=null) {
            String text="%"+query.query().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            result.add(cb.like(cb.lower(root.get("receiptNumber")),text,'!'));
        }
        return result.toArray(Predicate[]::new);
    }
}
