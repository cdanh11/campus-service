package com.campus.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FeeDefinition(UUID id, String code, String name, BigDecimal amount, String currency,
                            FeeStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public FeeDefinition {
        if (id == null || status == null || rowVersion < 0 || createdAt == null || updatedAt == null || !"VND".equals(currency))
            throw new IllegalArgumentException("Invalid fee");
        code = FinanceValues.code(code); name = FinanceValues.text(name, 160); amount = FinanceValues.amount(amount);
    }
}
