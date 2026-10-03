package com.campus.finance.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface PaymentRepository {
    Optional<ManualPayment> find(UUID id);
    ManualPayment lock(UUID id);
    ManualPayment create(ManualPayment value);
    ManualPayment update(ManualPayment value, long expectedVersion);
    BigDecimal totalRecorded(UUID chargeId);
    Optional<ChargeBalance> balance(UUID chargeId);
    PageResult<ManualPayment> search(PaymentSearch query);
}
