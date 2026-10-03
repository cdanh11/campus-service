package com.campus.academic.domain;

import java.util.Set;

/** Validated query shared by both catalogs; persistence still belongs to each catalog. */
public record AcademicCatalogSearch(int page, int size, String query, AcademicCatalogStatus status,
                                    String sortField, boolean ascending) {
    public AcademicCatalogSearch {
        query = query == null || query.trim().isEmpty() ? null : query.trim();
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || (query != null && query.codePointCount(0, query.length()) > 100)
                || !Set.of("code", "name", "title", "credits", "status", "createdAt", "updatedAt").contains(sortField)) {
            throw new IllegalArgumentException("Invalid catalog query");
        }
    }
}
