package com.campus.event.domain;

import java.util.Set;

public record EventSearch(int page, int size, String query, CampusEvent.Status status,
                          String sortField, boolean ascending) {
    private static final Set<String> SORTS = Set.of("code", "title", "startsAt", "endsAt", "capacity", "status", "createdAt", "updatedAt");
    public EventSearch {
        query = EventValues.trim(query);
        if (query != null && query.isEmpty()) query = null;
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || sortField == null || !SORTS.contains(sortField)
                || query != null && query.codePointCount(0, query.length()) > 100)
            throw new IllegalArgumentException("Invalid event query");
    }
}
