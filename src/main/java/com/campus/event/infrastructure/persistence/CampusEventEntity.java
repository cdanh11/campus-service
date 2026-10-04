package com.campus.event.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import com.campus.event.domain.CampusEvent;
import jakarta.persistence.*;

@Entity @Table(name="campus_events")
public class CampusEventEntity {
    @Id UUID id;
    @Column(nullable=false,length=32) String code;
    @Column(nullable=false,length=160) String title;
    @Column(nullable=false,length=4000) String description;
    @Column(name="starts_at",nullable=false) Instant startsAt;
    @Column(name="ends_at",nullable=false) Instant endsAt;
    @Column(nullable=false) int capacity;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) CampusEvent.Status status;
    @Version @Column(name="row_version",nullable=false) long version;
    @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
    @Column(name="updated_at",nullable=false) Instant updatedAt;
    protected CampusEventEntity() { }
    CampusEventEntity(CampusEvent event) { id=event.id(); createdAt=event.createdAt(); update(event); }
    void update(CampusEvent event) {
        code=event.code(); title=event.title(); description=event.description(); startsAt=event.startsAt(); endsAt=event.endsAt();
        capacity=event.capacity(); status=event.status(); updatedAt=event.updatedAt();
    }
    CampusEvent domain() { return new CampusEvent(id,code,title,description,startsAt,endsAt,capacity,status,version,createdAt,updatedAt); }
}
