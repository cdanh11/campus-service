package com.campus.academic.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.campus.academic.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.shared.application.time.BusinessCalendar;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.application.StudentProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentAcademicService {
    private final StudentAccountDirectory accounts;
    private final EnrollmentRepository enrollments;
    private final ClassSectionRepository sections;
    private final CourseOfferingRepository offerings;
    private final AcademicCourseRepository courses;
    private final AcademicTermRepository terms;
    private final FacultyStaffManagementService faculty;
    private final BusinessCalendar calendar;

    public StudentAcademicService(StudentAccountDirectory accounts, EnrollmentRepository enrollments,
                                  ClassSectionRepository sections, CourseOfferingRepository offerings,
                                  AcademicCourseRepository courses, AcademicTermRepository terms,
                                  FacultyStaffManagementService faculty, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.enrollments = enrollments;
        this.sections = sections;
        this.offerings = offerings;
        this.courses = courses;
        this.terms = terms;
        this.faculty = faculty;
        this.calendar = calendar;
    }

    public Timetable timetable(UUID account, UUID termId) {
        UUID studentId = student(account);
        var enrolled = views(studentId, EnrollmentStatus.ENROLLED);
        TermView term = termId == null ? current(enrolled) : termView(terms.findById(termId)
                .orElseThrow(AcademicDeliveryService.ResourceNotFoundException::new));
        var meetings = enrolled.stream()
                .filter(value -> term != null && value.term().id().equals(term.id()))
                .sorted(Comparator.comparing((EnrollmentView value) -> value.schedule().weekday() == null)
                        .thenComparing(value -> value.schedule().weekday(), Comparator.nullsLast(Integer::compare))
                        .thenComparing(value -> value.schedule().startsAt(), Comparator.nullsLast(java.time.LocalTime::compareTo))
                        .thenComparing(EnrollmentView::sectionCode))
                .toList();
        return new Timetable(studentId, term, meetings);
    }

    public Enrollments enrollments(UUID account) {
        UUID studentId = student(account);
        var items = views(studentId, null).stream()
                .sorted(Comparator.comparing((EnrollmentView value) -> value.term().startDate()).reversed()
                        .thenComparing(value -> value.course().code()).thenComparing(EnrollmentView::sectionCode))
                .toList();
        return new Enrollments(studentId, items);
    }

    private UUID student(UUID account) {
        return accounts.findByAccount(account).orElseThrow(StudentProfileNotFoundException::new).studentId();
    }

    private TermView current(List<EnrollmentView> values) {
        LocalDate today = calendar.today();
        var active = values.stream().map(EnrollmentView::term)
                .filter(value -> value.status() == AcademicTermStatus.ACTIVE).toList();
        var ongoing = active.stream()
                .filter(value -> !value.startDate().isAfter(today) && !value.endDate().isBefore(today))
                .max(Comparator.comparing(TermView::startDate));
        if (ongoing.isPresent()) return ongoing.get();
        var upcoming = active.stream().filter(value -> value.startDate().isAfter(today))
                .min(Comparator.comparing(TermView::startDate));
        if (upcoming.isPresent()) return upcoming.get();
        return active.stream().max(Comparator.comparing(TermView::startDate))
                .orElseGet(() -> values.stream().map(EnrollmentView::term)
                        .max(Comparator.comparing(TermView::startDate)).orElse(null));
    }

    private List<EnrollmentView> views(UUID studentId, EnrollmentStatus status) {
        var rows = rows(studentId, status);
        var sectionById = index(sections.findAllById(ids(rows, Enrollment::sectionId)), ClassSection::id);
        var offeringById = index(offerings.findAllById(sectionById.values().stream().map(ClassSection::offeringId).toList()), CourseOffering::id);
        var courseById = index(courses.findAllById(offeringById.values().stream().map(CourseOffering::courseId).toList()), AcademicCourse::id);
        var termById = index(terms.findAllById(offeringById.values().stream().map(CourseOffering::termId).toList()), AcademicTerm::id);
        var facultyBySection = facultyNames(sectionById.values());
        return rows.stream().map(row -> {
            var section = required(sectionById, row.sectionId());
            var offering = required(offeringById, section.offeringId());
            var course = required(courseById, offering.courseId());
            var term = required(termById, offering.termId());
            return new EnrollmentView(row.id(), row.status(), row.createdAt(), termView(term),
                    new CourseView(course.id(), course.code(), course.title(), course.credits()),
                    section.id(), section.code(), section.facultyId(), facultyBySection.get(section.facultyId()),
                    section.schedule(), section.status());
        }).toList();
    }

    private List<Enrollment> rows(UUID studentId, EnrollmentStatus status) {
        var result = new ArrayList<Enrollment>();
        for (int page = 0; ; page++) {
            var values = enrollments.search(new EnrollmentSearch(page, 100, studentId, null, status, "createdAt", true));
            result.addAll(values.content());
            if (values.content().isEmpty() || result.size() >= values.totalElements()) return result;
        }
    }

    private Map<UUID, String> facultyNames(Collection<ClassSection> values) {
        var ids = values.stream().map(ClassSection::facultyId).filter(Objects::nonNull).distinct().toList();
        return faculty.lookup(ids).stream().collect(Collectors.toMap(member -> member.id(), member -> member.fullName()));
    }

    private static List<UUID> ids(List<Enrollment> rows, Function<Enrollment, UUID> id) {
        return rows.stream().map(id).distinct().toList();
    }

    private static <T> Map<UUID, T> index(List<T> values, Function<T, UUID> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static <T> T required(Map<UUID, T> values, UUID id) {
        var value = values.get(id);
        if (value == null) throw new AcademicDeliveryService.ResourceNotFoundException();
        return value;
    }

    private static TermView termView(AcademicTerm term) {
        return new TermView(term.id(), term.code(), term.name(), term.startDate(), term.endDate(), term.status());
    }

    public record TermView(UUID id, String code, String name, LocalDate startDate, LocalDate endDate, AcademicTermStatus status) { }
    public record CourseView(UUID id, String code, String title, int credits) { }
    public record EnrollmentView(UUID enrollmentId, EnrollmentStatus status, Instant enrolledAt, TermView term, CourseView course,
                                 UUID sectionId, String sectionCode, UUID facultyId, String facultyName,
                                 SectionSchedule schedule, AcademicDeliveryStatus sectionStatus) { }
    public record Timetable(UUID studentId, TermView term, List<EnrollmentView> meetings) { }
    public record Enrollments(UUID studentId, List<EnrollmentView> items) { }
}
