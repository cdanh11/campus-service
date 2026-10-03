package com.campus.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudentCharge(UUID id, String chargeNumber, UUID studentId, UUID feeId, String feeCode, String feeName,
                            BigDecimal amount, String currency, LocalDate dueDate, ChargeStatus status,
                            long rowVersion, Instant createdAt, Instant updatedAt) {
    public StudentCharge {
        if (id == null || studentId == null || feeId == null || dueDate == null || status == null || rowVersion < 0
                || createdAt == null || updatedAt == null || !"VND".equals(currency)) throw new IllegalArgumentException("Invalid charge");
        chargeNumber = FinanceValues.code(chargeNumber); feeCode = FinanceValues.code(feeCode);
        feeName = FinanceValues.text(feeName, 160); amount = FinanceValues.amount(amount);
    }
    public StudentCharge cancel(Instant time) {
        if (status != ChargeStatus.OPEN) throw new IllegalStateException("Charge already cancelled");
        return new StudentCharge(id, chargeNumber, studentId, feeId, feeCode, feeName, amount, currency, dueDate,
                ChargeStatus.CANCELLED, rowVersion, createdAt, time);
    }
}
