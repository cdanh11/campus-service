package com.campus.academic.domain;

import java.time.LocalTime;

/** Optional weekly meeting slot for a class section; all meeting fields are stored together. */
public record SectionSchedule(Integer weekday, LocalTime startsAt, LocalTime endsAt, String room) {
    public SectionSchedule {
        if (weekday == null && startsAt == null && endsAt == null) {
            if (room != null && !room.isBlank()) throw new IllegalArgumentException("schedule requires weekday, start and end time");
            room = null;
        } else {
            if (weekday == null || weekday < 1 || weekday > 7 || startsAt == null || endsAt == null)
                throw new IllegalArgumentException("schedule requires weekday, start and end time");
            if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("schedule end must follow start");
            if (room != null) {
                room = room.trim();
                if (room.isEmpty()) room = null;
                else if (room.length() < 2 || room.length() > 32) throw new IllegalArgumentException("schedule room must be 2 to 32 characters");
            }
        }
    }
    public static SectionSchedule of(Integer weekday, LocalTime startsAt, LocalTime endsAt, String room) {
        return new SectionSchedule(weekday, startsAt, endsAt, room);
    }
    public boolean scheduled() { return weekday != null; }
    public static SectionSchedule empty() { return new SectionSchedule(null, null, null, null); }
}
