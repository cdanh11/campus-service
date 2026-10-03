package com.campus.finance.domain;

import java.util.Set;
import java.util.UUID;

public record PaymentSearch(int page, int size, String query, UUID chargeId, PaymentStatus status, String sortField, boolean ascending) {
    public PaymentSearch {
        query = FinanceValues.trim(query); if (query != null && query.isEmpty()) query = null;
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || query != null && query.codePointCount(0, query.length()) > 100 || sortField == null
                || !Set.of("receiptNumber","amount","status","recordedAt","createdAt","updatedAt").contains(sortField))
            throw new IllegalArgumentException("Invalid payment query");
    }
}
