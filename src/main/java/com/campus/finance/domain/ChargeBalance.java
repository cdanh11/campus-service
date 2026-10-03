package com.campus.finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record ChargeBalance(UUID chargeId, BigDecimal amount, String currency, ChargeStatus status,
                            long rowVersion, BigDecimal paidAmount, BigDecimal outstandingAmount) {
    public ChargeBalance(UUID id, BigDecimal amount, String currency, ChargeStatus status, long version, BigDecimal paid) {
        this(id, amount, currency, status, version, paid, status == ChargeStatus.CANCELLED ? BigDecimal.ZERO : amount.subtract(paid));
    }
    public ChargeBalance {
        amount = FinanceValues.amount(amount);
        if (chargeId == null || !"VND".equals(currency) || status == null || rowVersion < 0 || paidAmount == null || outstandingAmount == null)
            throw new IllegalArgumentException("Invalid charge balance");
        paidAmount = paidAmount.setScale(0, RoundingMode.UNNECESSARY); outstandingAmount = outstandingAmount.setScale(0, RoundingMode.UNNECESSARY);
        if (paidAmount.signum() < 0 || outstandingAmount.signum() < 0
                || (status == ChargeStatus.CANCELLED ? paidAmount.signum() != 0 || outstandingAmount.signum() != 0
                    : amount.compareTo(paidAmount.add(outstandingAmount)) != 0))
            throw new IllegalArgumentException("Invalid charge balance");
    }
}
