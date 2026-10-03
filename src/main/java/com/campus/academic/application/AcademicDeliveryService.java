package com.campus.academic.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.campus.academic.domain.*;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.PersonnelStatus;
import com.campus.personnel.domain.PersonnelType;
import com.campus.shared.application.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parent locks are always taken term -> offering -> section to serialize lifecycle checks. */
@Service
@Transactional
public class AcademicDeliveryService {
    private final AcademicTermRepository terms;
    private final CourseOfferingRepository offerings;
    private final ClassSectionRepository sections;
    private final AcademicCatalogService catalog;
    private final OrganizationUnitManagementService units;
    private final FacultyStaffManagementService personnel;
    private final Clock clock;

    public AcademicDeliveryService(AcademicTermRepository terms, CourseOfferingRepository offerings, ClassSectionRepository sections,
                                   AcademicCatalogService catalog, OrganizationUnitManagementService units,
                                   FacultyStaffManagementService personnel, Clock clock) {
        this.terms = terms; this.offerings = offerings; this.sections = sections;
        this.catalog = catalog; this.units = units; this.personnel = personnel; this.clock = clock;
    }

    public AcademicTerm createTerm(String code, String name, LocalDate start, LocalDate end) {
        var now = clock.instant();
        return terms.create(new AcademicTerm(UUID.randomUUID(), code, name, start, end, AcademicTermStatus.PLANNED, 0, now, now));
    }

    public AcademicTerm updateTerm(UUID id, String code, String name, LocalDate start, LocalDate end,
                                   AcademicTermStatus status, long version) {
        var old = terms.lock(id);
        version(old.rowVersion(), version);
        AcademicLifecycle.term(old.status(), status);
        if (old.status() != AcademicTermStatus.PLANNED && (!old.startDate().equals(start) || !old.endDate().equals(end))) {
            throw new InvalidStateException();
        }
        if (status == AcademicTermStatus.CLOSED && offerings.hasOpenOfferings(id)) throw new InvalidStateException();
        return terms.update(new AcademicTerm(id, code, name, start, end, status, old.rowVersion(), old.createdAt(), clock.instant()), version);
    }

    public CourseOffering createOffering(UUID termId, UUID courseId) {
        var term = terms.lock(termId);
        editable(term);
        var course = activeCourse(courseId);
        unit(course.organizationUnitId());
        var now = clock.instant();
        return offerings.create(new CourseOffering(UUID.randomUUID(), termId, courseId, course.organizationUnitId(), AcademicDeliveryStatus.DRAFT, 0, now, now));
    }

    public CourseOffering updateOffering(UUID id, AcademicDeliveryStatus status, long version) {
        var known = offering(id);
        var term = terms.lock(known.termId());
        var old = offerings.lock(id);
        version(old.rowVersion(), version);
        AcademicLifecycle.delivery(old.status(), status);
        if (status == AcademicDeliveryStatus.OPEN) {
            if (term.status() != AcademicTermStatus.ACTIVE) throw new InvalidStateException();
            activeCourse(old.courseId());
            unit(old.organizationUnitId());
        }
        if (status == AcademicDeliveryStatus.CLOSED && sections.hasOpenSections(id)) throw new InvalidStateException();
        return offerings.update(new CourseOffering(id, old.termId(), old.courseId(), old.organizationUnitId(), status,
                old.rowVersion(), old.createdAt(), clock.instant()), version);
    }

    public ClassSection createSection(UUID offeringId, String code, int capacity, UUID facultyId) {
        var known = offering(offeringId);
        editable(terms.lock(known.termId()));
        var offering = offerings.lock(offeringId);
        if (offering.status() == AcademicDeliveryStatus.CLOSED || offering.status() == AcademicDeliveryStatus.CANCELLED) throw new InvalidStateException();
        activeCourse(offering.courseId());
        unit(offering.organizationUnitId());
        faculty(facultyId, false);
        var now = clock.instant();
        return sections.create(new ClassSection(UUID.randomUUID(), offeringId, code, capacity, facultyId, AcademicDeliveryStatus.DRAFT, 0, now, now));
    }

    public ClassSection updateSection(UUID id, String code, int capacity, UUID facultyId, AcademicDeliveryStatus status, long version) {
        var known = section(id);
        var knownOffering = offering(known.offeringId());
        var term = terms.lock(knownOffering.termId());
        var offering = offerings.lock(known.offeringId());
        var old = sections.lock(id);
        version(old.rowVersion(), version);
        AcademicLifecycle.delivery(old.status(), status);
        if (old.status() != AcademicDeliveryStatus.DRAFT && (capacity != old.capacity() || !Objects.equals(facultyId, old.facultyId())
                || !old.code().equals(ClassSection.normalizeCode(code)))) throw new InvalidStateException();
        if (status == AcademicDeliveryStatus.OPEN) {
            if (term.status() != AcademicTermStatus.ACTIVE || offering.status() != AcademicDeliveryStatus.OPEN) throw new InvalidStateException();
            activeCourse(offering.courseId());
            unit(offering.organizationUnitId());
            faculty(facultyId, true);
        } else if (!Objects.equals(old.facultyId(), facultyId)) {
            faculty(facultyId, false);
        }
        return sections.update(new ClassSection(id, old.offeringId(), code, capacity, facultyId, status,
                old.rowVersion(), old.createdAt(), clock.instant()), version);
    }

    @Transactional(readOnly = true)
    public AcademicTerm term(UUID id) { return terms.findById(id).orElseThrow(ResourceNotFoundException::new); }
    @Transactional(readOnly = true)
    public CourseOffering offering(UUID id) { return offerings.findById(id).orElseThrow(ResourceNotFoundException::new); }
    @Transactional(readOnly = true)
    public ClassSection section(UUID id) { return sections.findById(id).orElseThrow(ResourceNotFoundException::new); }

    @Transactional(readOnly = true)
    public PageResult<AcademicTerm> terms(AcademicDeliverySearch query) {
        validateQuery(query, Set.of("code", "name", "startDate", "endDate", "status", "createdAt", "updatedAt"), true);
        return terms.search(query);
    }
    @Transactional(readOnly = true)
    public PageResult<CourseOffering> offerings(AcademicDeliverySearch query) {
        validateQuery(query, Set.of("status", "createdAt", "updatedAt"), false);
        if (query.query() != null) throw new InvalidQueryException();
        return offerings.search(query);
    }
    @Transactional(readOnly = true)
    public PageResult<ClassSection> sections(AcademicDeliverySearch query) {
        validateQuery(query, Set.of("code", "capacity", "status", "createdAt", "updatedAt"), false);
        return sections.search(query);
    }

    private void validateQuery(AcademicDeliverySearch query, Set<String> fields, boolean term) {
        if (!fields.contains(query.sortField())) throw new InvalidQueryException();
        try {
            if (query.status() != null) {
                if (term) AcademicTermStatus.valueOf(query.status()); else AcademicDeliveryStatus.valueOf(query.status());
            }
        } catch (IllegalArgumentException exception) { throw new InvalidQueryException(); }
    }
    private void editable(AcademicTerm term) {
        if (term.status() != AcademicTermStatus.PLANNED && term.status() != AcademicTermStatus.ACTIVE) throw new InvalidStateException();
    }
    private void version(long actual, long expected) { if (actual != expected) throw new StaleVersionException(); }
    private AcademicCourse activeCourse(UUID id) {
        try {
            var course = catalog.course(id);
            if (course.status() != AcademicCatalogStatus.ACTIVE) throw new ReferenceUnavailableException();
            return course;
        } catch (AcademicCatalogService.CourseNotFoundException exception) { throw new ReferenceUnavailableException(); }
    }
    private void unit(UUID id) {
        try {
            if (units.get(id).status() != OrganizationUnitStatus.ACTIVE) throw new ReferenceUnavailableException();
        } catch (OrganizationUnitManagementService.OrganizationUnitNotFoundException exception) { throw new ReferenceUnavailableException(); }
    }
    private void faculty(UUID id, boolean required) {
        if (id == null) {
            if (required) throw new ReferenceUnavailableException();
            return;
        }
        try {
            var member = personnel.get(id);
            if (member.status() != PersonnelStatus.ACTIVE || member.personnelType() != PersonnelType.FACULTY) throw new ReferenceUnavailableException();
        } catch (FacultyStaffManagementService.FacultyStaffNotFoundException exception) { throw new ReferenceUnavailableException(); }
    }

    public static final class ResourceNotFoundException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class InvalidStateException extends RuntimeException { }
    public static final class ReferenceUnavailableException extends RuntimeException { }
    public static final class InvalidQueryException extends RuntimeException { }
}
