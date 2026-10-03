package com.campus.academic.api;

import java.util.Set;
import com.campus.academic.domain.AcademicCatalogSearch;
import com.campus.academic.domain.AcademicCatalogStatus;

final class AcademicCatalogQuery {
    private AcademicCatalogQuery() { }

    static AcademicCatalogSearch parse(int page, int size, String query, String status, String sort, Set<String> fields) {
        try {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !fields.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
                throw new IllegalArgumentException();
            }
            return new AcademicCatalogSearch(page, size, query, status == null ? null : AcademicCatalogStatus.valueOf(status),
                    parts[0], parts[1].equals("asc"));
        } catch (IllegalArgumentException exception) {
            throw new InvalidQueryParameterException();
        }
    }

    static final class InvalidQueryParameterException extends RuntimeException { }
}
