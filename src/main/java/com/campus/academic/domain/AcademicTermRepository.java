package com.campus.academic.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AcademicTermRepository {
    AcademicTerm create(AcademicTerm value);
    AcademicTerm update(AcademicTerm value, long expectedVersion);
    List<AcademicTerm> findAllById(Collection<UUID> ids);
    Optional<AcademicTerm> findById(UUID id);
    AcademicTerm lock(UUID id);
    PageResult<AcademicTerm> search(AcademicDeliverySearch query);
}
