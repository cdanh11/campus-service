package com.campus.event.infrastructure.persistence;

import java.util.*;
import com.campus.event.application.EventCatalogService;
import com.campus.event.domain.*;
import com.campus.shared.application.PageResult;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

@Repository
class EventPersistenceAdapter implements EventRepository {
    private final EntityManager entities;
    EventPersistenceAdapter(EntityManager entities) { this.entities=entities; }
    public CampusEvent create(CampusEvent value) {
        var entity = new CampusEventEntity(value); entities.persist(entity); save(entity); return entity.domain();
    }
    public Optional<CampusEvent> find(UUID id) { return Optional.ofNullable(entities.find(CampusEventEntity.class,id)).map(CampusEventEntity::domain); }
    private CampusEventEntity locked(UUID id) {
        var entity = entities.find(CampusEventEntity.class,id);
        if (entity == null) throw new EventCatalogService.NotFoundException();
        entities.refresh(entity,LockModeType.PESSIMISTIC_WRITE); return entity;
    }
    public CampusEvent lock(UUID id) { return locked(id).domain(); }
    public CampusEvent update(CampusEvent value,long expectedVersion) {
        var entity = locked(value.id());
        if (entity.version != expectedVersion) throw new EventCatalogService.StaleVersionException();
        entity.update(value); save(entity); return entity.domain();
    }
    private void save(CampusEventEntity entity) { entities.flush(); entities.refresh(entity); }
    public long consumedSeats(UUID eventId) {
        return entities.createQuery("select count(r) from EventRegistrationEntity r where r.eventId=:event and r.status in :statuses",Long.class)
                .setParameter("event",eventId).setParameter("statuses",List.of(EventRegistration.Status.REGISTERED,EventRegistration.Status.ATTENDED)).getSingleResult();
    }
    public EventRegistration createRegistration(EventRegistration value) {
        var entity=new EventRegistrationEntity(value); entities.persist(entity); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public Optional<EventRegistration> registration(UUID id) { return Optional.ofNullable(entities.find(EventRegistrationEntity.class,id)).map(EventRegistrationEntity::domain); }
    public Optional<EventRegistration> ownedRegistration(UUID id,UUID studentId) {
        return entities.createQuery("select r from EventRegistrationEntity r where r.id=:id and r.studentId=:student",EventRegistrationEntity.class)
                .setParameter("id",id).setParameter("student",studentId).getResultStream().findFirst().map(EventRegistrationEntity::domain);
    }
    public Optional<EventRegistration> membership(UUID eventId,UUID studentId) {
        return entities.createQuery("select r from EventRegistrationEntity r where r.eventId=:event and r.studentId=:student",EventRegistrationEntity.class)
                .setParameter("event",eventId).setParameter("student",studentId).getResultStream().findFirst().map(EventRegistrationEntity::domain);
    }
    private EventRegistrationEntity lockedRegistration(UUID id) {
        var entity=entities.find(EventRegistrationEntity.class,id);
        if(entity==null) throw new EventCatalogService.NotFoundException();
        entities.refresh(entity,LockModeType.PESSIMISTIC_WRITE); return entity;
    }
    public EventRegistration lockRegistration(UUID id) { return lockedRegistration(id).domain(); }
    public EventRegistration updateRegistration(EventRegistration value,long expectedVersion) {
        var entity=lockedRegistration(value.id());
        if(entity.version!=expectedVersion) throw new EventCatalogService.StaleVersionException();
        entity.update(value); entities.flush(); entities.refresh(entity); return entity.domain();
    }
    public PageResult<EventRegistration> registrations(RegistrationSearch search) {
        var cb=entities.getCriteriaBuilder(); var query=cb.createQuery(EventRegistrationEntity.class); var root=query.from(EventRegistrationEntity.class);
        query.where(registrationFilters(cb,root,search));
        query.orderBy(search.ascending()?cb.asc(root.get(search.sortField())):cb.desc(root.get(search.sortField())),cb.asc(root.get("id")));
        var content=entities.createQuery(query).setFirstResult(search.page()*search.size()).setMaxResults(search.size()).getResultList();
        var count=cb.createQuery(Long.class); var other=count.from(EventRegistrationEntity.class);
        count.select(cb.count(other)).where(registrationFilters(cb,other,search));
        return new PageResult<>(content.stream().map(EventRegistrationEntity::domain).toList(),entities.createQuery(count).getSingleResult());
    }
    private Predicate[] registrationFilters(CriteriaBuilder cb,Root<EventRegistrationEntity> root,RegistrationSearch search) {
        var filters=new ArrayList<Predicate>();
        if(search.eventId()!=null) filters.add(cb.equal(root.get("eventId"),search.eventId()));
        if(search.studentId()!=null) filters.add(cb.equal(root.get("studentId"),search.studentId()));
        if(search.status()!=null) filters.add(cb.equal(root.get("status"),search.status()));
        return filters.toArray(Predicate[]::new);
    }
    public PageResult<CampusEvent> search(EventSearch search) {
        var cb=entities.getCriteriaBuilder(); var query=cb.createQuery(CampusEventEntity.class); var root=query.from(CampusEventEntity.class);
        query.where(filters(cb,root,search));
        query.orderBy(search.ascending()?cb.asc(root.get(search.sortField())):cb.desc(root.get(search.sortField())),cb.asc(root.get("id")));
        var content=entities.createQuery(query).setFirstResult(search.page()*search.size()).setMaxResults(search.size()).getResultList();
        var count=cb.createQuery(Long.class); var other=count.from(CampusEventEntity.class);
        count.select(cb.count(other)).where(filters(cb,other,search));
        return new PageResult<>(content.stream().map(CampusEventEntity::domain).toList(),entities.createQuery(count).getSingleResult());
    }
    private Predicate[] filters(CriteriaBuilder cb,Root<CampusEventEntity> root,EventSearch search) {
        var predicates=new ArrayList<Predicate>();
        if (search.status()!=null) predicates.add(cb.equal(root.get("status"),search.status()));
        if (search.query()!=null) {
            String literal="%"+search.query().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            predicates.add(cb.or(cb.like(cb.lower(root.get("code")),literal,'!'),cb.like(cb.lower(root.get("title")),literal,'!')));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
