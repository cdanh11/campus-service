package com.campus.academic.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicTermRepository {
    AcademicTerm create(AcademicTerm value);
    AcademicTerm update(AcademicTerm value, long expectedVersion);
    Optional<AcademicTerm> findById(UUID id);
    AcademicTerm lock(UUID id);
    PageResult<AcademicTerm> search(AcademicDeliverySearch query);
}
