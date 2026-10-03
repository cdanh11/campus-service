package com.campus.dormitory.domain;

import java.util.Set;
import java.util.UUID;

public record InventorySearch(int page, int size, String query, InventoryStatus status, UUID parentId,
                              String sortField, boolean ascending) {
    public InventorySearch {
        query = InventoryItem.trim(query);
        if (query != null && query.isEmpty()) query = null;
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || query != null && query.codePointCount(0, query.length()) > 100
                || sortField == null || !Set.of("code", "name", "status", "createdAt", "updatedAt").contains(sortField)) {
            throw new IllegalArgumentException("Invalid inventory query");
        }
    }
}
