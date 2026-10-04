package com.campus.event.application;

import java.time.Clock;
import java.util.UUID;
import com.campus.event.domain.*;
import com.campus.shared.application.PageResult;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class EventRegistrationService {
    public enum Action { CANCEL, RESTORE, ATTEND }
    private final EventRepository events;
    private final StudentAccountDirectory students;
    private final EventAudit audit;
    private final Clock clock;
    public EventRegistrationService(EventRepository events,StudentAccountDirectory students,EventAudit audit,Clock clock) {
        this.events=events; this.students=students; this.audit=audit; this.clock=clock;
    }
    public EventRegistration register(UUID actor,UUID eventId,UUID studentId) {
        actor(actor); var event=events.lock(eventId); return admit(actor,event,studentId);
    }
    public EventRegistration registerOwn(UUID actor,UUID eventId) {
        actor(actor); var event=events.lock(eventId); return admit(actor,event,linked(actor));
    }
    private EventRegistration admit(UUID actor,CampusEvent event,UUID studentId) {
        eligibility(event,studentId);
        if(events.membership(event.id(),studentId).isPresent()) throw new DuplicateMembershipException();
        capacity(event);
        var saved=events.createRegistration(EventRegistration.register(UUID.randomUUID(),event.id(),studentId,clock.instant()));
        record(actor,saved,"REGISTERED"); return saved;
    }
    public EventRegistration change(UUID actor,UUID id,Action action,long expectedVersion) {
        actor(actor); var old=events.registration(id).orElseThrow(NotFoundException::new);
        var event=events.lock(old.eventId()); return mutate(actor,event,events.lockRegistration(id),action,expectedVersion);
    }
    public EventRegistration changeOwn(UUID actor,UUID id,Action action,long expectedVersion) {
        actor(actor); if(action==Action.ATTEND) throw new ForbiddenActionException();
        var old=events.ownedRegistration(id,linked(actor)).orElseThrow(NotFoundException::new);
        var event=events.lock(old.eventId()); var current=events.lockRegistration(id);
        // Re-evaluate current ownership after waiting for the event lock, without foreign module locks.
        if(!current.studentId().equals(linked(actor))) throw new NotFoundException();
        return mutate(actor,event,current,action,expectedVersion);
    }
    private EventRegistration mutate(UUID actor,CampusEvent event,EventRegistration old,Action action,long expectedVersion) {
        EventValues.version(expectedVersion);
        if(old.rowVersion()!=expectedVersion) throw new EventCatalogService.StaleVersionException();
        if(action==null) throw new IllegalArgumentException("Registration action required");
        EventRegistration next;
        try {
            next=switch(action) {
                case CANCEL -> old.cancel(clock.instant());
                case ATTEND -> old.attend(event.status(),clock.instant());
                case RESTORE -> {
                    if(old.status()!=EventRegistration.Status.CANCELLED) throw new IllegalStateException("Only cancelled membership can be restored");
                    eligibility(event,old.studentId()); capacity(event); yield old.restore(clock.instant());
                }
            };
        } catch(IllegalStateException failure) { throw new EventCatalogService.InvalidStateException(); }
        var saved=events.updateRegistration(next,expectedVersion);
        record(actor,saved,switch(action){case CANCEL -> "CANCELLED"; case RESTORE -> "RESTORED"; case ATTEND -> "ATTENDED";}); return saved;
    }
    private void eligibility(CampusEvent event,UUID studentId) {
        if(event.status()!=CampusEvent.Status.OPEN) throw new EventCatalogService.InvalidStateException();
        if(studentId==null || students.findByStudent(studentId).filter(student -> student.status()==StudentStatus.ACTIVE).isEmpty())
            throw new StudentUnavailableException();
    }
    private void capacity(CampusEvent event) {
        if(events.consumedSeats(event.id())>=event.capacity()) throw new CapacityExceededException();
    }
    private UUID linked(UUID actor) { return students.findByAccount(actor).orElseThrow(NotFoundException::new).studentId(); }
    private void record(UUID actor,EventRegistration saved,String action) { audit.record(actor,EventAudit.Resource.REGISTRATION,saved.id(),action,saved.rowVersion(),saved.status().name(),clock.instant()); }
    private void actor(UUID actor) { if(actor==null) throw new IllegalArgumentException("Authenticated actor required"); }
    @Transactional(readOnly=true) public EventRegistration get(UUID id) { return events.registration(id).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly=true) public EventRegistration own(UUID actor,UUID id) { actor(actor); return events.ownedRegistration(id,linked(actor)).orElseThrow(NotFoundException::new); }
    @Transactional(readOnly=true) public PageResult<EventRegistration> search(RegistrationSearch search) { return events.registrations(search); }
    @Transactional(readOnly=true) public PageResult<EventRegistration> ownSearch(UUID actor,RegistrationSearch search) {
        actor(actor); return events.registrations(new RegistrationSearch(search.page(),search.size(),search.eventId(),linked(actor),search.status(),search.sortField(),search.ascending()));
    }
    public static final class NotFoundException extends RuntimeException { }
    public static final class DuplicateMembershipException extends RuntimeException { }
    public static final class StudentUnavailableException extends RuntimeException { }
    public static final class CapacityExceededException extends RuntimeException { }
    public static final class ForbiddenActionException extends RuntimeException { }
}
