package com.campus.academic.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface CourseOfferingRepository {
    CourseOffering create(CourseOffering value);
    CourseOffering update(CourseOffering value, long expectedVersion);
    List<CourseOffering> findAllById(Collection<UUID> ids);
    List<CourseOffering> findByTermIdAndStatus(UUID termId, AcademicDeliveryStatus status);
    Optional<CourseOffering> findById(UUID id);
    CourseOffering lock(UUID id);
    PageResult<CourseOffering> search(AcademicDeliverySearch query);
    boolean hasOpenOfferings(UUID termId);
}
