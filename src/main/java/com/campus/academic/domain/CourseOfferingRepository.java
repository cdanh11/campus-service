package com.campus.academic.domain;

import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface CourseOfferingRepository {
    CourseOffering create(CourseOffering value);
    CourseOffering update(CourseOffering value, long expectedVersion);
    Optional<CourseOffering> findById(UUID id);
    CourseOffering lock(UUID id);
    PageResult<CourseOffering> search(AcademicDeliverySearch query);
    boolean hasOpenOfferings(UUID termId);
}
