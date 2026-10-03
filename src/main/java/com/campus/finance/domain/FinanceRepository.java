package com.campus.finance.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface FinanceRepository {
    Optional<FeeDefinition> fee(UUID id);
    FeeDefinition lockFee(UUID id);
    FeeDefinition createFee(FeeDefinition value);
    FeeDefinition updateFee(FeeDefinition value, long expectedVersion);
    PageResult<FeeDefinition> fees(FinanceSearch query);
    Optional<StudentCharge> charge(UUID id);
    StudentCharge lockCharge(UUID id);
    StudentCharge createCharge(StudentCharge value);
    StudentCharge updateCharge(StudentCharge value, long expectedVersion);
    PageResult<StudentCharge> charges(FinanceSearch query);
}
