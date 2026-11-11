package com.campus.academic.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.campus.academic.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CurriculumService {
    private final CurriculumCourseRepository curriculum;
    private final AcademicProgramRepository programs;
    private final AcademicCourseRepository courses;
    private final Clock clock;

    public CurriculumService(CurriculumCourseRepository curriculum, AcademicProgramRepository programs,
                             AcademicCourseRepository courses, Clock clock) {
        this.curriculum = curriculum;
        this.programs = programs;
        this.courses = courses;
        this.clock = clock;
    }

    public CurriculumCourse create(UUID programId, UUID courseId, int semesterIndex, UUID prerequisiteCourseId) {
        if (programs.findById(programId).isEmpty()) throw new ProgramNotFoundException();
        if (courses.findById(courseId).isEmpty()) throw new CourseNotFoundException();
        prerequisite(prerequisiteCourseId);
        if (curriculum.exists(programId, courseId)) throw new DuplicateException();
        try {
            var value = CurriculumCourse.create(UUID.randomUUID(), programId, courseId, semesterIndex,
                    prerequisiteCourseId, clock.instant());
            try {
                return curriculum.create(value);
            } catch (DataIntegrityViolationException exception) {
                throw new DuplicateException();
            }
        } catch (CurriculumCourse.InvalidCurriculumException exception) {
            throw new RequestValidationException();
        }
    }

    public CurriculumCourse update(UUID id, int semesterIndex, UUID prerequisiteCourseId, long expectedVersion) {
        var current = get(id);
        prerequisite(prerequisiteCourseId);
        try {
            return curriculum.update(current.update(semesterIndex, prerequisiteCourseId, clock.instant()), expectedVersion);
        } catch (CurriculumCourse.InvalidCurriculumException exception) {
            throw new RequestValidationException();
        }
    }

    @Transactional(readOnly = true)
    public CurriculumCourse get(UUID id) { return curriculum.findById(id).orElseThrow(NotFoundException::new); }

    @Transactional(readOnly = true)
    public List<CurriculumCourse> entries(UUID programId) {
        if (programs.findById(programId).isEmpty()) throw new ProgramNotFoundException();
        return curriculum.findByProgramId(programId);
    }

    @Transactional(readOnly = true)
    public List<EntryView> views(UUID programId) {
        var entries = entries(programId);
        var courseById = index(courses.findAllById(entries.stream().flatMap(entry -> Stream.of(entry.courseId(), entry.prerequisiteCourseId()))
                .filter(Objects::nonNull).distinct().toList()));
        return entries.stream().map(entry -> {
            var course = courseById.get(entry.courseId());
            var prerequisite = entry.prerequisiteCourseId() == null ? null : courseById.get(entry.prerequisiteCourseId());
            return new EntryView(entry.id(), entry.programId(), entry.courseId(), course == null ? null : course.code(),
                    course == null ? null : course.title(), course == null ? 0 : course.credits(), entry.semesterIndex(),
                    entry.prerequisiteCourseId(), prerequisite == null ? null : prerequisite.code(),
                    prerequisite == null ? null : prerequisite.title(), entry.rowVersion());
        }).toList();
    }

    private Map<UUID, AcademicCourse> index(List<AcademicCourse> values) {
        return values.stream().collect(Collectors.toMap(AcademicCourse::id, value -> value));
    }

    private void prerequisite(UUID id) {
        if (id != null && courses.findById(id).isEmpty()) throw new PrerequisiteNotFoundException();
    }

    public record EntryView(UUID id, UUID programId, UUID courseId, String courseCode, String courseTitle, int credits,
                            int semesterIndex, UUID prerequisiteCourseId, String prerequisiteCode,
                            String prerequisiteTitle, long rowVersion) { }
    public static final class NotFoundException extends RuntimeException { }
    public static final class ProgramNotFoundException extends RuntimeException { }
    public static final class CourseNotFoundException extends RuntimeException { }
    public static final class PrerequisiteNotFoundException extends RuntimeException { }
    public static final class DuplicateException extends RuntimeException { }
    public static final class StaleVersionException extends RuntimeException { }
    public static final class RequestValidationException extends RuntimeException { }
}
