package com.campus.event.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface EventRepository {
    CampusEvent create(CampusEvent event);
    Optional<CampusEvent> find(UUID id);
    CampusEvent lock(UUID id);
    CampusEvent update(CampusEvent event, long expectedVersion);
    PageResult<CampusEvent> search(EventSearch search);
    long consumedSeats(UUID eventId);
    EventRegistration createRegistration(EventRegistration registration);
    Optional<EventRegistration> registration(UUID id);
    Optional<EventRegistration> ownedRegistration(UUID id,UUID studentId);
    Optional<EventRegistration> membership(UUID eventId,UUID studentId);
    EventRegistration lockRegistration(UUID id);
    EventRegistration updateRegistration(EventRegistration registration,long expectedVersion);
    PageResult<EventRegistration> registrations(RegistrationSearch search);
}
