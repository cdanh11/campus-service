package com.campus.finance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ManualPayment(UUID id, String receiptNumber, UUID chargeId, BigDecimal amount, String currency,
                            PaymentStatus status, long rowVersion, Instant recordedAt, Instant reversedAt,
                            String reversalReason, Instant createdAt, Instant updatedAt) {
    public ManualPayment {
        if (id == null || chargeId == null || !"VND".equals(currency) || status == null || rowVersion < 0
                || recordedAt == null || createdAt == null || updatedAt == null
                || (status == PaymentStatus.RECORDED ? reversedAt != null || reversalReason != null : reversedAt == null || reversalReason == null)
                || reversedAt != null && reversedAt.isBefore(recordedAt)) throw new IllegalArgumentException("Invalid manual payment");
        receiptNumber = FinanceValues.code(receiptNumber); amount = FinanceValues.amount(amount);
        if (reversalReason != null) reversalReason = FinanceValues.text(reversalReason, 500);
    }
    public ManualPayment reverse(String reason, Instant time) {
        if (status != PaymentStatus.RECORDED) throw new IllegalStateException("Payment already reversed");
        return new ManualPayment(id, receiptNumber, chargeId, amount, currency, PaymentStatus.REVERSED, rowVersion, recordedAt,
                time, reason, createdAt, time);
    }
}
