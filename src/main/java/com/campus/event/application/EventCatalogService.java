package com.campus.event.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.campus.event.domain.*;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class EventCatalogService {
    private final EventRepository events;
    private final EventAudit audit;
    private final OrganizationUnitManagementService units;
    private final Clock clock;
    public EventCatalogService(EventRepository events, EventAudit audit, OrganizationUnitManagementService units, Clock clock) {
        this.events = events; this.audit = audit; this.units = units; this.clock = clock;
    }
    public CampusEvent create(UUID actor, String code, String title, String description, Instant startsAt, Instant endsAt, int capacity) {
        return create(actor,code,title,description,startsAt,endsAt,capacity,null);
    }
    public CampusEvent create(UUID actor, String code, String title, String description, Instant startsAt, Instant endsAt,
                              int capacity, UUID hostUnitId) {
        actor(actor); host(hostUnitId);
        var saved = events.create(CampusEvent.draft(UUID.randomUUID(),code,title,description,startsAt,endsAt,capacity,clock.instant(),hostUnitId));
        audit.record(actor,saved.id(),"CREATED",saved.rowVersion(),saved.status().name(),clock.instant());
        return saved;
    }
    public CampusEvent update(UUID actor, UUID id, String code, String title, String description, Instant startsAt,
                              Instant endsAt, int capacity, CampusEvent.Status status, long expectedVersion) {
        return update(actor,id,code,title,description,startsAt,endsAt,capacity,status,expectedVersion,null);
    }
    public CampusEvent update(UUID actor, UUID id, String code, String title, String description, Instant startsAt,
                              Instant endsAt, int capacity, CampusEvent.Status status, long expectedVersion, UUID hostUnitId) {
        actor(actor); EventValues.version(expectedVersion); host(hostUnitId);
        var old = events.lock(id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        CampusEvent next;
        try { next = old.update(code,title,description,startsAt,endsAt,capacity,status,events.consumedSeats(id),clock.instant(),hostUnitId); }
        catch (IllegalStateException failure) { throw new InvalidStateException(); }
        var saved = events.update(next,expectedVersion);
        audit.record(actor,id,"UPDATED",saved.rowVersion(),saved.status().name(),clock.instant());
        return saved;
    }
    @Transactional(readOnly=true) public CampusEvent get(UUID id) { return events.find(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly=true) public List<CampusEvent> lookup(Collection<UUID> ids) { return events.findAllById(ids); }
    @Transactional(readOnly=true) public PageResult<CampusEvent> search(EventSearch search) { return events.search(search); }
    @Transactional(readOnly=true) public List<EventHost> hosts() {
        var ids = events.hostUnitIds();
        if (ids.isEmpty()) return List.of();
        return units.lookup(ids).stream().map(unit -> new EventHost(unit.id(),unit.name()))
                .sorted(Comparator.comparing(EventHost::name)).toList();
    }
    private void host(UUID hostUnitId) {
        if (hostUnitId == null) return;
        try { units.get(hostUnitId); }
        catch (OrganizationUnitManagementService.OrganizationUnitNotFoundException failure) {
            throw new IllegalArgumentException("Unknown event host");
        }
    }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    public record EventHost(UUID id, String name) { }
    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
}
