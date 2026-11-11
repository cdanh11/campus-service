package com.campus.academic.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.campus.academic.application.StudentAcademicService.EnrollmentView;
import com.campus.academic.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.student.application.StudentProfileService;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student self-service course registration. Cohorts are staggered in fixed two-day slots ordered
 * oldest first, so K21 and earlier open together and every later cohort starts two days behind the previous one.
 */
@Service
@Transactional
public class RegistrationService {
    static final int FIRST_COHORT = 21;
    static final int SLOT_DAYS = 2;
    static final int LATEST_COHORT = 26;

    private final StudentProfileService profiles;
    private final StudentRecordService records;
    private final EnrollmentViewAssembler assembler;
    private final AcademicTermRepository terms;
    private final CourseOfferingRepository offerings;
    private final ClassSectionRepository sections;
    private final EnrollmentRepository enrollments;
    private final AcademicCourseRepository courses;
    private final FacultyStaffManagementService faculty;
    private final Clock clock;

    public RegistrationService(StudentProfileService profiles, StudentRecordService records, EnrollmentViewAssembler assembler,
                               AcademicTermRepository terms, CourseOfferingRepository offerings, ClassSectionRepository sections,
                               EnrollmentRepository enrollments, AcademicCourseRepository courses,
                               FacultyStaffManagementService faculty, Clock clock) {
        this.profiles = profiles; this.records = records; this.assembler = assembler;
        this.terms = terms; this.offerings = offerings; this.sections = sections;
        this.enrollments = enrollments; this.courses = courses; this.faculty = faculty; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Registration registration(UUID account) {
        var student = profiles.profile(account);
        var term = registrationTerm();
        if (term == null) return new Registration(student.id(), student.studentNumber(), student.cohort(),
                student.programId(), student.programName(), null, List.of(), List.of());
        var now = clock.instant();
        var number = cohortNumber(student.cohort());
        var slot = number == null || term.registrationOpensAt() == null ? null : slot(term, number);
        var state = state(term, slot, student.cohort(), now);
        var enrolled = assembler.views(student.id(), EnrollmentStatus.ENROLLED).stream()
                .filter(view -> view.term().id().equals(term.id())).toList();
        var enrolledCourseIds = enrolled.stream().map(view -> view.course().id()).collect(Collectors.toSet());
        var flow = curriculumByCourse(account, student.programId());

        var openOfferings = offerings.findByTermIdAndStatus(term.id(), AcademicDeliveryStatus.OPEN);
        var offeringById = openOfferings.stream().collect(Collectors.toMap(CourseOffering::id, value -> value));
        var openSections = sections.findByOfferingIdsAndStatus(offeringById.keySet(), AcademicDeliveryStatus.OPEN);
        var courseById = courses.findAllById(openOfferings.stream().map(CourseOffering::courseId).distinct().toList())
                .stream().collect(Collectors.toMap(AcademicCourse::id, value -> value));
        var facultyNames = faculty.lookup(openSections.stream().map(ClassSection::facultyId).filter(Objects::nonNull)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(member -> member.id(), member -> member.fullName()));
        var occupied = enrollments.occupiedBySectionIds(openSections.stream().map(ClassSection::id).toList());

        var options = new ArrayList<SectionOption>();
        for (var section : openSections) {
            var offering = offeringById.get(section.offeringId());
            var course = offering == null ? null : courseById.get(offering.courseId());
            if (course == null) continue;
            var taken = enrolledCourseIds.contains(course.id());
            var capacity = section.capacity();
            var used = occupied.getOrDefault(section.id(), 0L);
            var entry = flow.get(course.id());
            var prerequisiteMet = entry == null || entry.prerequisiteSatisfied();
            var reason = state.equals(STATE_OPEN) ? taken ? REASON_ALREADY_ENROLLED
                    : !prerequisiteMet ? REASON_PREREQUISITE
                    : conflicts(section.schedule(), enrolled) ? REASON_SCHEDULE
                    : used >= capacity ? REASON_FULL : null : state;
            options.add(new SectionOption(section.id(), section.code(), offering.id(), course.id(), course.code(),
                    course.title(), course.credits(), course.courseGroup().name(), section.facultyId(),
                    facultyNames.get(section.facultyId()), section.schedule(), capacity, used,
                    (int) Math.max(0, capacity - used), taken, reason == null, reason,
                    entry == null ? null : entry.prerequisiteCode(), entry == null ? null : entry.prerequisiteTitle()));
        }
        options.sort(Comparator.comparing(SectionOption::courseCode).thenComparing(SectionOption::sectionCode));
        return new Registration(student.id(), student.studentNumber(), student.cohort(), student.programId(),
                student.programName(), window(term, slot, state), options, enrolled);
    }

    public Enrollment register(UUID account, UUID sectionId) {
        var student = profiles.profile(account);
        var knownSection = sections.findById(sectionId).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var knownOffering = offerings.findById(knownSection.offeringId())
                .orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var term = terms.lock(knownOffering.termId());
        var offering = offerings.lock(knownOffering.id());
        var section = sections.lock(sectionId);
        var now = clock.instant();
        if (student.status() != StudentStatus.ACTIVE) throw new EnrollmentService.StudentUnavailableException();
        if (term.status() != AcademicTermStatus.ACTIVE || offering.status() != AcademicDeliveryStatus.OPEN
                || section.status() != AcademicDeliveryStatus.OPEN) throw new EnrollmentService.InvalidStateException();
        requireOpen(term, student.cohort(), now);
        if (enrollments.exists(student.id(), section.id())
                || enrollments.hasActiveEnrollmentInOffering(student.id(), offering.id()))
            throw new EnrollmentService.DuplicateException();
        if (enrollments.occupied(section.id()) >= section.capacity()) throw new EnrollmentService.CapacityExceededException();
        requirePrerequisite(account, student.programId(), offering.courseId());
        requireNoConflict(student.id(), term.id(), section.schedule());
        return enrollments.create(new Enrollment(UUID.randomUUID(), student.id(), section.id(), EnrollmentStatus.ENROLLED, 0, now, now));
    }

    public void withdraw(UUID account, UUID enrollmentId) {
        var student = profiles.profile(account);
        var known = enrollments.findById(enrollmentId).orElseThrow(NotFoundException::new);
        if (!known.studentId().equals(student.id())) throw new NotFoundException();
        var knownSection = sections.findById(known.sectionId()).orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var knownOffering = offerings.findById(knownSection.offeringId())
                .orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new);
        var term = terms.lock(knownOffering.termId());
        offerings.lock(knownOffering.id());
        sections.lock(knownSection.id());
        var current = enrollments.lock(enrollmentId);
        if (current.status() != EnrollmentStatus.ENROLLED) throw new EnrollmentService.InvalidStateException();
        var now = clock.instant();
        requireOpen(term, student.cohort(), now);
        enrollments.update(current.withStatus(EnrollmentStatus.WITHDRAWN, now), current.rowVersion());
    }

    private AcademicTerm registrationTerm() {
        var active = terms.findAllByStatus(AcademicTermStatus.ACTIVE);
        var configured = active.stream().filter(term -> term.registrationOpensAt() != null).toList();
        if (configured.isEmpty()) return active.stream().max(Comparator.comparing(AcademicTerm::startDate)).orElse(null);
        var now = clock.instant();
        var open = configured.stream()
                .filter(term -> !now.isBefore(term.registrationOpensAt()) && now.isBefore(term.registrationClosesAt()))
                .max(Comparator.comparing(AcademicTerm::registrationOpensAt));
        if (open.isPresent()) return open.get();
        var upcoming = configured.stream().filter(term -> now.isBefore(term.registrationOpensAt()))
                .min(Comparator.comparing(AcademicTerm::registrationOpensAt));
        return upcoming.orElseGet(() -> configured.stream()
                .max(Comparator.comparing(AcademicTerm::registrationClosesAt)).orElseThrow());
    }

    private static Integer cohortNumber(String cohort) {
        if (cohort == null || !cohort.matches("K\\d{2}")) return null;
        return Integer.valueOf(cohort.substring(1));
    }

    private static CohortSlot slot(AcademicTerm term, int cohortNumber) {
        int index = Math.max(0, cohortNumber - FIRST_COHORT);
        var opensAt = term.registrationOpensAt().plus(Duration.ofDays((long) index * SLOT_DAYS));
        var closesAt = opensAt.plus(Duration.ofDays(SLOT_DAYS));
        if (closesAt.isAfter(term.registrationClosesAt())) closesAt = term.registrationClosesAt();
        return new CohortSlot("K" + cohortNumber, cohortNumber == FIRST_COHORT ? "K21 trở về trước" : "K" + cohortNumber,
                opensAt, closesAt);
    }

    private static String state(AcademicTerm term, CohortSlot slot, String cohort, Instant now) {
        if (term == null || term.registrationOpensAt() == null) return STATE_NOT_CONFIGURED;
        if (cohortNumber(cohort) == null) return STATE_COHORT_MISSING;
        if (now.isBefore(term.registrationOpensAt()) || now.isBefore(slot.opensAt())) return STATE_NOT_OPEN;
        if (!now.isBefore(term.registrationClosesAt()) || !now.isBefore(slot.closesAt())) return STATE_CLOSED;
        return STATE_OPEN;
    }

    private static void requireOpen(AcademicTerm term, String cohort, Instant now) {
        if (term.registrationOpensAt() == null) throw new WindowNotConfiguredException();
        var number = cohortNumber(cohort);
        if (number == null) throw new CohortMissingException();
        if (now.isBefore(term.registrationOpensAt())) throw new WindowNotOpenException();
        if (!now.isBefore(term.registrationClosesAt())) throw new WindowClosedException();
        var slot = slot(term, number);
        if (now.isBefore(slot.opensAt())) throw new WindowNotOpenException();
        if (!now.isBefore(slot.closesAt())) throw new WindowClosedException();
    }

    private Window window(AcademicTerm term, CohortSlot slot, String state) {
        var current = cohortNumber(slot == null ? null : slot.cohort());
        int last = Math.max(LATEST_COHORT, current == null ? LATEST_COHORT : current);
        var slots = new ArrayList<CohortSlot>();
        if (term.registrationOpensAt() != null) {
            for (int number = FIRST_COHORT; number <= last; number++) {
                var value = slot(term, number);
                slots.add(new CohortSlot(value.cohort(), value.label(), value.opensAt(), value.closesAt(),
                        current != null && current == number));
            }
        }
        return new Window(term.id(), term.code(), term.name(), term.startDate(), term.endDate(),
                term.registrationOpensAt(), term.registrationClosesAt(),
                slot == null ? null : slot.opensAt(), slot == null ? null : slot.closesAt(), state, slots);
    }

    private Map<UUID, StudentRecordService.CurriculumCourseStatus> curriculumByCourse(UUID account, UUID programId) {
        if (programId == null) return Map.of();
        var map = new HashMap<UUID, StudentRecordService.CurriculumCourseStatus>();
        for (var semester : records.curriculum(account).semesters())
            for (var course : semester.courses()) map.putIfAbsent(course.courseId(), course);
        return map;
    }

    private void requirePrerequisite(UUID account, UUID programId, UUID courseId) {
        var entry = curriculumByCourse(account, programId).get(courseId);
        if (entry != null && !entry.prerequisiteSatisfied()) throw new PrerequisiteNotMetException();
    }

    private void requireNoConflict(UUID studentId, UUID termId, SectionSchedule schedule) {
        var current = assembler.views(studentId, EnrollmentStatus.ENROLLED).stream()
                .filter(view -> view.term().id().equals(termId)).toList();
        if (conflicts(schedule, current)) throw new ScheduleConflictException();
    }

    private static boolean conflicts(SectionSchedule candidate, List<EnrollmentView> enrolled) {
        if (!candidate.scheduled()) return false;
        return enrolled.stream().anyMatch(view -> view.schedule().scheduled()
                && candidate.weekday().equals(view.schedule().weekday())
                && candidate.startsAt().isBefore(view.schedule().endsAt())
                && candidate.endsAt().isAfter(view.schedule().startsAt()));
    }

    public static final String STATE_OPEN = "OPEN";
    public static final String STATE_NOT_OPEN = "NOT_OPEN";
    public static final String STATE_CLOSED = "CLOSED";
    public static final String STATE_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String STATE_COHORT_MISSING = "COHORT_MISSING";
    public static final String REASON_ALREADY_ENROLLED = "ALREADY_ENROLLED";
    public static final String REASON_PREREQUISITE = "PREREQUISITE_NOT_MET";
    public static final String REASON_SCHEDULE = "SCHEDULE_CONFLICT";
    public static final String REASON_FULL = "SECTION_FULL";

    public record Registration(UUID studentId, String studentNumber, String cohort, UUID programId, String programName,
                               Window window, List<SectionOption> sections, List<EnrollmentView> enrolled) { }
    public record Window(UUID termId, String termCode, String termName, LocalDate termStartDate, LocalDate termEndDate,
                         Instant opensAt, Instant closesAt, Instant slotOpensAt, Instant slotClosesAt, String state,
                         List<CohortSlot> slots) { }
    public record CohortSlot(String cohort, String label, Instant opensAt, Instant closesAt, boolean current) {
        public CohortSlot(String cohort, String label, Instant opensAt, Instant closesAt) {
            this(cohort, label, opensAt, closesAt, false);
        }
    }
    public record SectionOption(UUID sectionId, String sectionCode, UUID offeringId, UUID courseId, String courseCode,
                                String courseTitle, int credits, String courseGroup, UUID facultyId, String facultyName,
                                SectionSchedule schedule, int capacity, long occupied, int remaining, boolean enrolled,
                                boolean eligible, String blockedReason, String prerequisiteCourseCode,
                                String prerequisiteCourseTitle) { }

    public static final class WindowNotConfiguredException extends RuntimeException { }
    public static final class WindowNotOpenException extends RuntimeException { }
    public static final class WindowClosedException extends RuntimeException { }
    public static final class CohortMissingException extends RuntimeException { }
    public static final class PrerequisiteNotMetException extends RuntimeException { }
    public static final class ScheduleConflictException extends RuntimeException { }
    public static final class NotFoundException extends RuntimeException { }
}
