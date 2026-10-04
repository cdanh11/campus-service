package com.campus.event.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.event.domain.EventRegistration;
import jakarta.persistence.*;

@Entity @Table(name="event_registrations")
public class EventRegistrationEntity {
    @Id UUID id;
    @Column(name="event_id",nullable=false,updatable=false) UUID eventId;
    @Column(name="student_id",nullable=false,updatable=false) UUID studentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) EventRegistration.Status status;
    @Version @Column(name="row_version",nullable=false) long version;
    @Column(name="registered_at",nullable=false) Instant registeredAt;
    @Column(name="cancelled_at") Instant cancelledAt;
    @Column(name="attended_at") Instant attendedAt;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected EventRegistrationEntity() { }
    EventRegistrationEntity(EventRegistration value) { id=value.id(); eventId=value.eventId(); studentId=value.studentId(); createdAt=value.createdAt(); update(value); }
    void update(EventRegistration value) {
        status=value.status(); registeredAt=value.registeredAt(); cancelledAt=value.cancelledAt(); attendedAt=value.attendedAt(); updatedAt=value.updatedAt();
    }
    EventRegistration domain() { return new EventRegistration(id,eventId,studentId,status,version,registeredAt,cancelledAt,attendedAt,createdAt,updatedAt); }
}
