package com.campus.event.domain;

import java.util.Set;
import java.util.UUID;

/** UUID filters concern owned membership fields; no implicit cross-module contact/name search. */
public record RegistrationSearch(int page,int size,UUID eventId,UUID studentId,EventRegistration.Status status,
                                 String sortField,boolean ascending) {
    private static final Set<String> SORTS=Set.of("registeredAt","status","createdAt","updatedAt");
    public RegistrationSearch {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE || sortField==null || !SORTS.contains(sortField))
            throw new IllegalArgumentException("Invalid registration query");
    }
}
