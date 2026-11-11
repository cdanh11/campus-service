package com.campus.academic.application;

import java.time.Clock;
import java.util.UUID;
import com.campus.academic.domain.*;
import com.campus.shared.application.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AcademicGradeService {
    private final AcademicGradeRepository grades;
    private final EnrollmentRepository enrollments;
    private final ClassSectionRepository sections;
    private final CourseOfferingRepository offerings;
    private final AcademicCourseRepository courses;
    private final Clock clock;

    public AcademicGradeService(AcademicGradeRepository grades, EnrollmentRepository enrollments,
                                ClassSectionRepository sections, CourseOfferingRepository offerings,
                                AcademicCourseRepository courses, Clock clock) {
        this.grades = grades;
        this.enrollments = enrollments;
        this.sections = sections;
        this.offerings = offerings;
        this.courses = courses;
        this.clock = clock;
    }

    public AcademicGrade create(UUID enrollmentId, GradeScores scores, UUID actor) {
        if (actor == null) throw new RequestValidationException();
        var course = course(enrollments.findById(enrollmentId).orElseThrow(NotFoundException::new));
        if (grades.findByEnrollmentId(enrollmentId).isPresent()) throw new DuplicateException();
        var scheme = GradeScheme.of(course.courseGroup(), course.hasFinalProject());
        try {
            var value = AcademicGrade.create(UUID.randomUUID(), enrollmentId, scheme, scores.attendanceScore(),
                    scores.midtermScore(), scores.finalScore(), scores.projectScore(), actor, clock.instant());
            return grades.create(value);
        } catch (AcademicGrade.InvalidGradeException exception) {
            throw new RequestValidationException();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateException();
        }
    }

    public AcademicGrade update(UUID enrollmentId, GradeScores scores, long expectedVersion, UUID actor) {
        if (expectedVersion < 0 || actor == null) throw new RequestValidationException();
        var current = grades.findByEnrollmentId(enrollmentId).orElseThrow(NotFoundException::new);
        if (current.rowVersion() != expectedVersion) throw new StaleVersionException();
        course(enrollments.findById(enrollmentId).orElseThrow(NotFoundException::new));
        try {
            return grades.update(current.withScores(scores.attendanceScore(), scores.midtermScore(),
                    scores.finalScore(), scores.projectScore(), actor, clock.instant()), expectedVersion);
        } catch (AcademicGrade.InvalidGradeException exception) {
            throw new RequestValidationException();
        }
    }

    @Transactional(readOnly = true)
    public AcademicGrade get(UUID id) { return grades.findById(id).orElseThrow(NotFoundException::new); }

    @Transactional(readOnly = true)
    public AcademicGrade byEnrollment(UUID enrollmentId) {
        return grades.findByEnrollmentId(enrollmentId).orElseThrow(NotFoundException::new);
    }

    @Transactional(readOnly = true)
    public PageResult<AcademicGrade> search(GradeSearch query) { return grades.search(query); }

    private AcademicCourse course(Enrollment enrollment) {
        var section = sections.findById(enrollment.sectionId()).orElseThrow(NotFoundException::new);
        var offering = offerings.findById(section.offeringId()).orElseThrow(NotFoundException::new);
        return courses.findById(offering.courseId()).orElseThrow(NotFoundException::new);
    }

    public record GradeScores(double attendanceScore, double midtermScore, Double finalScore, Double projectScore) { }
    public static final class NotFoundException extends RuntimeException { }
    public static final class DuplicateException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class RequestValidationException extends RuntimeException { }
}
