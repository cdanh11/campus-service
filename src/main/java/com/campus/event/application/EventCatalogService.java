package com.campus.event.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.campus.event.domain.*;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class EventCatalogService {
    private final EventRepository events;
    private final EventAudit audit;
    private final Clock clock;
    public EventCatalogService(EventRepository events, EventAudit audit, Clock clock) {
        this.events = events; this.audit = audit; this.clock = clock;
    }
    public CampusEvent create(UUID actor, String code, String title, String description, Instant startsAt, Instant endsAt, int capacity) {
        actor(actor);
        var saved = events.create(CampusEvent.draft(UUID.randomUUID(),code,title,description,startsAt,endsAt,capacity,clock.instant()));
        audit.record(actor,saved.id(),"CREATED",saved.rowVersion(),saved.status().name(),clock.instant());
        return saved;
    }
    public CampusEvent update(UUID actor, UUID id, String code, String title, String description, Instant startsAt,
                              Instant endsAt, int capacity, CampusEvent.Status status, long expectedVersion) {
        actor(actor); EventValues.version(expectedVersion);
        var old = events.lock(id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        CampusEvent next;
        try { next = old.update(code,title,description,startsAt,endsAt,capacity,status,events.consumedSeats(id),clock.instant()); }
        catch (IllegalStateException failure) { throw new InvalidStateException(); }
        var saved = events.update(next,expectedVersion);
        audit.record(actor,id,"UPDATED",saved.rowVersion(),saved.status().name(),clock.instant());
        return saved;
    }
    @Transactional(readOnly=true) public CampusEvent get(UUID id) { return events.find(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly=true) public PageResult<CampusEvent> search(EventSearch search) { return events.search(search); }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    public static final class NotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
}
