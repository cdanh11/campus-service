package com.campus.academic.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface ClassSectionRepository {
    ClassSection create(ClassSection value);
    ClassSection update(ClassSection value, long expectedVersion);
    List<ClassSection> findAllById(Collection<UUID> ids);
    List<ClassSection> findByOfferingIdsAndStatus(Collection<UUID> offeringIds, AcademicDeliveryStatus status);
    Optional<ClassSection> findById(UUID id);
    ClassSection lock(UUID id);
    PageResult<ClassSection> search(AcademicDeliverySearch query);
    boolean hasOpenSections(UUID offeringId);
}
