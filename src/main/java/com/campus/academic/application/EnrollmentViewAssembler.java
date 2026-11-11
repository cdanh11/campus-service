package com.campus.academic.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.campus.academic.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Joins enrollments to their section, offering, course and term for read-side views. */
@Component
@Transactional(readOnly = true)
public class EnrollmentViewAssembler {
    private final EnrollmentRepository enrollments;
    private final ClassSectionRepository sections;
    private final CourseOfferingRepository offerings;
    private final AcademicCourseRepository courses;
    private final AcademicTermRepository terms;
    private final FacultyStaffManagementService faculty;

    public EnrollmentViewAssembler(EnrollmentRepository enrollments, ClassSectionRepository sections,
                                   CourseOfferingRepository offerings, AcademicCourseRepository courses,
                                   AcademicTermRepository terms, FacultyStaffManagementService faculty) {
        this.enrollments = enrollments;
        this.sections = sections;
        this.offerings = offerings;
        this.courses = courses;
        this.terms = terms;
        this.faculty = faculty;
    }

    public List<StudentAcademicService.EnrollmentView> views(UUID studentId, EnrollmentStatus status) {
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
            return new StudentAcademicService.EnrollmentView(row.id(), row.status(), row.createdAt(), termView(term),
                    new StudentAcademicService.CourseView(course.id(), course.code(), course.title(), course.credits()),
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

    public static StudentAcademicService.TermView termView(AcademicTerm term) {
        return new StudentAcademicService.TermView(term.id(), term.code(), term.name(), term.startDate(), term.endDate(), term.status());
    }
}
