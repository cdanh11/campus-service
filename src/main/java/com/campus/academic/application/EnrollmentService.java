package com.campus.academic.application;

import java.time.Clock;
import java.util.UUID;
import com.campus.academic.domain.*;
import com.campus.shared.application.PageResult;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final AcademicTermRepository terms;
    private final CourseOfferingRepository offerings;
    private final ClassSectionRepository sections;
    private final StudentManagementService students;
    private final Clock clock;

    public EnrollmentService(EnrollmentRepository enrollments, AcademicTermRepository terms, CourseOfferingRepository offerings,
                             ClassSectionRepository sections, StudentManagementService students, Clock clock) {
        this.enrollments = enrollments; this.terms = terms; this.offerings = offerings;
        this.sections = sections; this.students = students; this.clock = clock;
    }

    public Enrollment create(UUID studentId, UUID sectionId) {
        var parents = lockParents(sectionId);
        if (enrollments.exists(studentId, sectionId)) throw new DuplicateException();
        eligible(studentId, parents);
        var now = clock.instant();
        return enrollments.create(new Enrollment(UUID.randomUUID(), studentId, sectionId, EnrollmentStatus.ENROLLED, 0, now, now));
    }

    public Enrollment update(UUID id, EnrollmentStatus status, long expectedVersion) {
        if (status == null || expectedVersion < 0) throw new IllegalArgumentException("Invalid enrollment mutation");
        var known = get(id);
        var parents = lockParents(known.sectionId());
        // Refresh under lock: a prior read may still be cached in the persistence context.
        var old = enrollments.lock(id);
        if (old.rowVersion() != expectedVersion) throw new StaleVersionException();
        if (old.status() == status) throw new InvalidStateException();
        if (status == EnrollmentStatus.ENROLLED) eligible(old.studentId(), parents);
        return enrollments.update(old.withStatus(status, clock.instant()), expectedVersion);
    }

    @Transactional(readOnly = true)
    public Enrollment get(UUID id) { return enrollments.findById(id).orElseThrow(NotFoundException::new); }

    @Transactional(readOnly = true)
    public PageResult<Enrollment> search(EnrollmentSearch query) { return enrollments.search(query); }

    private Parents lockParents(UUID sectionId) {
        var known = sections.findById(sectionId).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var offering = offerings.findById(known.offeringId()).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var term = terms.lock(offering.termId());
        offering = offerings.lock(offering.id());
        return new Parents(term, offering, sections.lock(sectionId));
    }

    private void eligible(UUID studentId, Parents parents) {
        try {
            if (students.get(studentId).status() != StudentStatus.ACTIVE) throw new StudentUnavailableException();
        } catch (StudentManagementService.StudentNotFoundException exception) { throw new StudentUnavailableException(); }
        if (parents.term().status() != AcademicTermStatus.ACTIVE || parents.offering().status() != AcademicDeliveryStatus.OPEN
                || parents.section().status() != AcademicDeliveryStatus.OPEN) throw new InvalidStateException();
        if (enrollments.occupied(parents.section().id()) >= parents.section().capacity()) throw new CapacityExceededException();
    }

    private record Parents(AcademicTerm term, CourseOffering offering, ClassSection section) { }
    public static final class NotFoundException extends RuntimeException { }
    public static final class DuplicateException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
    public static final class StudentUnavailableException extends RuntimeException { }
    public static final class CapacityExceededException extends RuntimeException { }
}
