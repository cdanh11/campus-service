package com.campus.academic.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface EnrollmentRepository {
    Enrollment create(Enrollment value);
    Enrollment update(Enrollment value, long expectedVersion);
    Optional<Enrollment> findById(UUID id);
    Enrollment lock(UUID id);
    boolean exists(UUID studentId, UUID sectionId);
    boolean hasActiveEnrollmentInOffering(UUID studentId, UUID offeringId);
    long occupied(UUID sectionId);
    Map<UUID, Long> occupiedBySectionIds(Collection<UUID> sectionIds);
    PageResult<Enrollment> search(EnrollmentSearch query);
}
