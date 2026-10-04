package com.campus.notification.infrastructure.persistence;

import java.util.*;
import java.util.function.Function;
import com.campus.notification.application.NotificationService;
import com.campus.notification.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class NotificationPersistenceAdapter implements NotificationRepository {
    private final EntityManager entities;
    NotificationPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public NotificationTemplate createTemplate(NotificationTemplate value) { var e=new NotificationTemplateEntity(value); insert(e); return e.domain(); }
    public Optional<NotificationTemplate> template(UUID id) { return Optional.ofNullable(entities.find(NotificationTemplateEntity.class,id)).map(NotificationTemplateEntity::domain); }
    public NotificationTemplate lockTemplate(UUID id) { return lock(NotificationTemplateEntity.class,id).domain(); }
    public NotificationTemplate updateTemplate(NotificationTemplate value,long expectedVersion) {
        var e=lock(NotificationTemplateEntity.class,value.id()); version(e.version,expectedVersion); e.update(value); save(e); return e.domain();
    }
    public PageResult<NotificationTemplate> templates(NotificationSearch q) { return search(NotificationTemplateEntity.class,q,NotificationSearch.Kind.TEMPLATE,null,NotificationTemplateEntity::domain); }
    public Notice createNotice(Notice value) { var e=new NoticeEntity(value); insert(e); return e.domain(); }
    public Optional<Notice> notice(UUID id) { return Optional.ofNullable(entities.find(NoticeEntity.class,id)).map(NoticeEntity::domain); }
    public Notice lockNotice(UUID id) { return lock(NoticeEntity.class,id).domain(); }
    public Notice updateNotice(Notice value,long expectedVersion) { var e=lock(NoticeEntity.class,value.id()); version(e.version,expectedVersion); e.update(value); save(e); return e.domain(); }
    public PageResult<Notice> notices(NotificationSearch q) { return search(NoticeEntity.class,q,NotificationSearch.Kind.NOTICE,null,NoticeEntity::domain); }
    public List<Notice> noticesByIds(Set<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return entities.createQuery("select n from NoticeEntity n where n.id in :ids",NoticeEntity.class).setParameter("ids",ids).getResultList().stream().map(NoticeEntity::domain).toList();
    }
    public NotificationDelivery createDelivery(NotificationDelivery value) { var e=new NotificationDeliveryEntity(value); insert(e); return e.domain(); }
    private Optional<NotificationDeliveryEntity> owned(UUID id,UUID recipient) {
        return entities.createQuery("select d from NotificationDeliveryEntity d where d.id=:id and d.recipientId=:recipient",NotificationDeliveryEntity.class)
                .setParameter("id",id).setParameter("recipient",recipient).getResultStream().findFirst();
    }
    public Optional<NotificationDelivery> ownedDelivery(UUID id,UUID recipient) { return owned(id,recipient).map(NotificationDeliveryEntity::domain); }
    public NotificationDelivery lockOwnedDelivery(UUID id,UUID recipient) {
        var e=owned(id,recipient).orElseThrow(NotificationService.NotFoundException::new);
        entities.refresh(e,LockModeType.PESSIMISTIC_WRITE); return e.domain();
    }
    public NotificationDelivery updateDelivery(NotificationDelivery value,long expectedVersion) {
        lockOwnedDelivery(value.id(),value.recipientId()); var e=entities.find(NotificationDeliveryEntity.class,value.id());
        version(e.version,expectedVersion); e.update(value); save(e); return e.domain();
    }
    public PageResult<NotificationDelivery> inbox(UUID recipient,NotificationSearch q) { return search(NotificationDeliveryEntity.class,q,NotificationSearch.Kind.INBOX,recipient,NotificationDeliveryEntity::domain); }
    private void insert(Object e) { entities.persist(e); save(e); }
    private void save(Object e) { entities.flush(); entities.refresh(e); }
    private <T> T lock(Class<T> type,UUID id) {
        var e=entities.find(type,id); if (e==null) throw new NotificationService.NotFoundException();
        entities.refresh(e,LockModeType.PESSIMISTIC_WRITE); return e;
    }
    private void version(long actual,long expected) { if (actual!=expected) throw new NotificationService.StaleVersionException(); }
    private <T,D> PageResult<D> search(Class<T> type,NotificationSearch q,NotificationSearch.Kind kind,UUID owner,Function<T,D> map) {
        q.validate(kind); var cb=entities.getCriteriaBuilder(); var select=cb.createQuery(type); var root=select.from(type);
        select.where(filters(cb,root,q,kind,owner)); var field=root.get(q.sortField());
        select.orderBy(q.ascending()?cb.asc(field):cb.desc(field),cb.asc(root.get("id")));
        var rows=entities.createQuery(select).setFirstResult(q.page()*q.size()).setMaxResults(q.size()).getResultList();
        var count=cb.createQuery(Long.class); var other=count.from(type); count.select(cb.count(other)).where(filters(cb,other,q,kind,owner));
        return new PageResult<>(rows.stream().map(map).toList(),entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb,Root<?> root,NotificationSearch q,NotificationSearch.Kind kind,UUID owner) {
        var result=new ArrayList<Predicate>();
        if (kind==NotificationSearch.Kind.INBOX) result.add(cb.equal(root.get("recipientId"),Objects.requireNonNull(owner)));
        if (q.status()!=null) {
            Object status=switch(kind) { case TEMPLATE -> NotificationTemplate.Status.valueOf(q.status()); case NOTICE -> Notice.Status.valueOf(q.status()); case INBOX -> NotificationDelivery.Status.valueOf(q.status()); };
            result.add(cb.equal(root.get("status"),status));
        }
        if (q.query()!=null) {
            String text="%"+q.query().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            if (kind==NotificationSearch.Kind.TEMPLATE) result.add(cb.or(cb.like(cb.lower(root.get("code")),text,'!'),cb.like(cb.lower(root.get("name")),text,'!')));
            else result.add(cb.like(cb.lower(root.get("title")),text,'!'));
        }
        return result.toArray(Predicate[]::new);
    }
}
