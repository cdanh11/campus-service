package com.campus.dormitory.domain;
import java.util.Optional;
import java.util.UUID;
import com.campus.shared.application.PageResult;

public interface AssignmentRepository {
    Optional<AccommodationAssignment> find(UUID id);
    AccommodationAssignment lock(UUID id);
    AccommodationAssignment create(AccommodationAssignment value);
    AccommodationAssignment update(AccommodationAssignment value, long expectedVersion);
    boolean hasCurrentBed(UUID bedId);
    boolean hasCurrentStudent(UUID studentId);
    PageResult<AccommodationAssignment> search(AssignmentSearch query);
}
