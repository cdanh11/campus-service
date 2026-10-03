package com.campus.academic.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import com.campus.academic.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.campus.academic.application.AcademicAudit.Action.*;
import static com.campus.academic.application.AcademicAudit.Resource.*;

/** All HTTP mutations pass through this transaction; fixtures/provisioning use the core services. */
@Service
@Transactional
public class AcademicAdministrationService {
    private final AcademicCatalogService catalog;
    private final AcademicDeliveryService delivery;
    private final EnrollmentService enrollments;
    private final AcademicAudit audit;
    private final Clock clock;
    public AcademicAdministrationService(AcademicCatalogService catalog, AcademicDeliveryService delivery,
                                         EnrollmentService enrollments, AcademicAudit audit, Clock clock) {
        this.catalog = catalog; this.delivery = delivery; this.enrollments = enrollments; this.audit = audit; this.clock = clock;
    }

    public AcademicProgram createProgram(UUID actor, String code, String name, UUID unit, AcademicCatalogStatus status) {
        actor(actor); var value = catalog.createProgram(code, name, unit, status);
        record(actor, PROGRAM, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public AcademicProgram updateProgram(UUID actor, UUID id, String code, String name, UUID unit, AcademicCatalogStatus status, long version) {
        actor(actor); var value = catalog.updateProgram(id, code, name, unit, status, version);
        record(actor, PROGRAM, value.id(), UPDATED, value.rowVersion(), value.status().name()); return value;
    }
    public AcademicCourse createCourse(UUID actor, String code, String title, int credits, UUID unit, AcademicCatalogStatus status) {
        actor(actor); var value = catalog.createCourse(code, title, credits, unit, status);
        record(actor, COURSE, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public AcademicCourse updateCourse(UUID actor, UUID id, String code, String title, int credits, UUID unit, AcademicCatalogStatus status, long version) {
        actor(actor); var value = catalog.updateCourse(id, code, title, credits, unit, status, version);
        record(actor, COURSE, value.id(), UPDATED, value.rowVersion(), value.status().name()); return value;
    }
    public AcademicTerm createTerm(UUID actor, String code, String name, LocalDate start, LocalDate end) {
        actor(actor); var value = delivery.createTerm(code, name, start, end);
        record(actor, TERM, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public AcademicTerm updateTerm(UUID actor, UUID id, String code, String name, LocalDate start, LocalDate end, AcademicTermStatus status, long version) {
        actor(actor); var value = delivery.updateTerm(id, code, name, start, end, status, version);
        record(actor, TERM, value.id(), UPDATED, value.rowVersion(), value.status().name()); return value;
    }
    public CourseOffering createOffering(UUID actor, UUID term, UUID course) {
        actor(actor); var value = delivery.createOffering(term, course);
        record(actor, COURSE_OFFERING, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public CourseOffering updateOffering(UUID actor, UUID id, AcademicDeliveryStatus status, long version) {
        actor(actor); var value = delivery.updateOffering(id, status, version);
        record(actor, COURSE_OFFERING, value.id(), UPDATED, value.rowVersion(), value.status().name()); return value;
    }
    public ClassSection createSection(UUID actor, UUID offering, String code, int capacity, UUID faculty) {
        actor(actor); var value = delivery.createSection(offering, code, capacity, faculty);
        record(actor, CLASS_SECTION, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public ClassSection updateSection(UUID actor, UUID id, String code, int capacity, UUID faculty, AcademicDeliveryStatus status, long version) {
        actor(actor); var value = delivery.updateSection(id, code, capacity, faculty, status, version);
        record(actor, CLASS_SECTION, value.id(), UPDATED, value.rowVersion(), value.status().name()); return value;
    }
    public Enrollment create(UUID actor, UUID student, UUID section) {
        actor(actor); var value = enrollments.create(student, section);
        record(actor, ENROLLMENT, value.id(), CREATED, value.rowVersion(), value.status().name()); return value;
    }
    public Enrollment update(UUID actor, UUID id, EnrollmentStatus status, long version) {
        actor(actor); var value = enrollments.update(id, status, version);
        record(actor, ENROLLMENT, value.id(), status == EnrollmentStatus.WITHDRAWN ? WITHDRAWN : REENROLLED, value.rowVersion(), value.status().name()); return value;
    }
    private void actor(UUID actor) { if (actor == null) throw new IllegalArgumentException("Authenticated actor required"); }
    private void record(UUID actor, AcademicAudit.Resource resource, UUID target, AcademicAudit.Action action, long version, String status) {
        audit.record(actor, resource, target, action, version, status, clock.instant());
    }
}
