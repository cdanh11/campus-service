package com.campus.event.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CampusEvent(UUID id, String code, String title, String description, Instant startsAt,
                          Instant endsAt, int capacity, Status status, long rowVersion,
                          Instant createdAt, Instant updatedAt) {
    public enum Status { DRAFT, OPEN, CLOSED, CANCELLED }
    public CampusEvent {
        Objects.requireNonNull(id); Objects.requireNonNull(startsAt); Objects.requireNonNull(endsAt);
        Objects.requireNonNull(status); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        code = EventValues.code(code); title = EventValues.text(title, 160); description = EventValues.text(description, 4000);
        EventValues.version(rowVersion);
        if (!startsAt.isBefore(endsAt) || capacity < 1) throw new IllegalArgumentException("Invalid event schedule or capacity");
    }
    public static CampusEvent draft(UUID id, String code, String title, String description, Instant startsAt,
                                    Instant endsAt, int capacity, Instant now) {
        return new CampusEvent(id, code, title, description, startsAt, endsAt, capacity, Status.DRAFT, 0, now, now);
    }
    public CampusEvent update(String code, String title, String description, Instant startsAt, Instant endsAt,
                              int capacity, Status nextStatus, long consumedSeats, Instant now) {
        Objects.requireNonNull(nextStatus);
        if (status == Status.CLOSED || status == Status.CANCELLED
                || status == Status.DRAFT && nextStatus == Status.CLOSED
                || status == Status.OPEN && nextStatus == Status.DRAFT)
            throw new IllegalStateException("Invalid event transition");
        if (consumedSeats < 0 || consumedSeats > capacity) throw new IllegalArgumentException("Invalid event capacity");
        return new CampusEvent(id, code, title, description, startsAt, endsAt, capacity, nextStatus, rowVersion, createdAt, now);
    }
}
