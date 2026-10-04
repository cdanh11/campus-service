package com.campus.event.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Retained membership facts; re-registration strategy is deliberately not decided by this record. */
public record EventRegistration(UUID id, UUID eventId, UUID studentId, Status status, long rowVersion,
                                Instant registeredAt, Instant cancelledAt, Instant attendedAt,
                                Instant createdAt, Instant updatedAt) {
    public enum Status { REGISTERED, CANCELLED, ATTENDED }
    public EventRegistration {
        Objects.requireNonNull(id); Objects.requireNonNull(eventId); Objects.requireNonNull(studentId);
        Objects.requireNonNull(status); Objects.requireNonNull(registeredAt); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        EventValues.version(rowVersion);
        boolean valid = switch (status) {
            case REGISTERED -> cancelledAt == null && attendedAt == null;
            case CANCELLED -> cancelledAt != null && !cancelledAt.isBefore(registeredAt) && attendedAt == null;
            case ATTENDED -> attendedAt != null && !attendedAt.isBefore(registeredAt) && cancelledAt == null;
        };
        if (!valid) throw new IllegalArgumentException("Invalid event registration history");
    }
    public static EventRegistration register(UUID id,UUID eventId,UUID studentId,Instant now) {
        return new EventRegistration(id,eventId,studentId,Status.REGISTERED,0,now,null,null,now,now);
    }
    public EventRegistration cancel(Instant now) {
        if (status != Status.REGISTERED) throw new IllegalStateException("Only registered membership can be cancelled");
        return new EventRegistration(id,eventId,studentId,Status.CANCELLED,rowVersion,registeredAt,now,null,createdAt,now);
    }
    public EventRegistration attend(CampusEvent.Status eventStatus,Instant now) {
        Objects.requireNonNull(eventStatus);
        if (status != Status.REGISTERED || eventStatus != CampusEvent.Status.OPEN && eventStatus != CampusEvent.Status.CLOSED)
            throw new IllegalStateException("Invalid event attendance");
        return new EventRegistration(id,eventId,studentId,Status.ATTENDED,rowVersion,registeredAt,null,now,createdAt,now);
    }
    public boolean consumesSeat() { return status != Status.CANCELLED; }
    public EventRegistration restore(Instant now) {
        if (status != Status.CANCELLED) throw new IllegalStateException("Only cancelled membership can be restored");
        if (Objects.requireNonNull(now).isBefore(cancelledAt)) throw new IllegalArgumentException("Invalid restoration time");
        return new EventRegistration(id,eventId,studentId,Status.REGISTERED,rowVersion,now,null,null,createdAt,now);
    }
}
