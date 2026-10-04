package com.campus.library.domain;

import java.util.Set;
import java.util.UUID;

public record LibrarySearch(Resource resource, int page, int size, String query, UUID titleId,
                            UUID copyId, UUID studentId, String status, String sortField, boolean ascending) {
    public enum Resource { TITLE, COPY, LOAN }
    public LibrarySearch {
        query = LibraryValues.trim(query);
        if (query != null && query.isEmpty()) query = null;
        if (resource == null || page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || query != null && query.codePointCount(0, query.length()) > 100)
            throw new IllegalArgumentException("Invalid library query");
        Set<String> sorts = switch (resource) {
            case TITLE -> Set.of("code", "title", "author", "status", "createdAt", "updatedAt");
            case COPY -> Set.of("code", "status", "createdAt", "updatedAt");
            case LOAN -> Set.of("borrowedAt", "dueAt", "returnedAt", "status", "createdAt", "updatedAt");
        };
        if (sortField == null || !sorts.contains(sortField)) throw new IllegalArgumentException("Invalid library sort");
        if (status != null) {
            if (resource == Resource.LOAN) BookLoan.Status.valueOf(status); else LibraryStatus.valueOf(status);
        }
        if (resource == Resource.TITLE && (titleId != null || copyId != null || studentId != null)
                || resource == Resource.COPY && (copyId != null || studentId != null)
                || resource == Resource.LOAN && (titleId != null || query != null))
            throw new IllegalArgumentException("Unsupported library filter");
    }
}
