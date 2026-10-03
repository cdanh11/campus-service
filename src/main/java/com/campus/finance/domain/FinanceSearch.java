package com.campus.finance.domain;

import java.util.Set;
import java.util.UUID;

public record FinanceSearch(int page, int size, String query, String status, UUID studentId, UUID feeId,
                            String sortField, boolean ascending) {
    public FinanceSearch {
        query = FinanceValues.trim(query);
        if (query != null && query.isEmpty()) query = null;
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || query != null && query.codePointCount(0, query.length()) > 100 || sortField == null)
            throw new IllegalArgumentException("Invalid Finance query");
    }
    public void fees() {
        if (studentId != null || feeId != null || !Set.of("code", "name", "amount", "status", "createdAt", "updatedAt").contains(sortField))
            throw new IllegalArgumentException("Invalid fee query");
        if (status != null) FeeStatus.valueOf(status);
    }
    public void charges() {
        if (!Set.of("chargeNumber", "amount", "dueDate", "status", "createdAt", "updatedAt").contains(sortField))
            throw new IllegalArgumentException("Invalid charge query");
        if (status != null) ChargeStatus.valueOf(status);
    }
}
