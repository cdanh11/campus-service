package com.campus.finance.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import com.campus.shared.application.PageResult;

public interface FinanceRepository {
    Optional<FeeDefinition> fee(UUID id);
    List<FeeDefinition> feesByIds(Collection<UUID> ids);
    FeeDefinition lockFee(UUID id);
    FeeDefinition createFee(FeeDefinition value);
    FeeDefinition updateFee(FeeDefinition value, long expectedVersion);
    PageResult<FeeDefinition> fees(FinanceSearch query);
    Optional<StudentCharge> charge(UUID id);
    List<StudentCharge> chargesByIds(Collection<UUID> ids);
    StudentCharge lockCharge(UUID id);
    StudentCharge createCharge(StudentCharge value);
    StudentCharge updateCharge(StudentCharge value, long expectedVersion);
    StudentCharge advanceChargeVersion(UUID id, long expectedVersion, Instant time);
    PageResult<StudentCharge> charges(FinanceSearch query);
}
