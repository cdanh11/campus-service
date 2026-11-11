package com.campus.academic.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CurriculumCourse(UUID id, UUID programId, UUID courseId, int semesterIndex,
                               UUID prerequisiteCourseId, long rowVersion, Instant createdAt, Instant updatedAt) {
    public CurriculumCourse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(programId, "programId");
        Objects.requireNonNull(courseId, "courseId");
        if (semesterIndex < 1 || semesterIndex > 12) throw new InvalidCurriculumException("semesterIndex");
        if (courseId.equals(prerequisiteCourseId)) throw new InvalidCurriculumException("prerequisiteSelfReference");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (rowVersion < 0) throw new InvalidCurriculumException("rowVersion");
    }

    public static CurriculumCourse create(UUID id, UUID programId, UUID courseId, int semesterIndex,
                                          UUID prerequisiteCourseId, Instant now) {
        return new CurriculumCourse(id, programId, courseId, semesterIndex, prerequisiteCourseId, 0, now, now);
    }

    public CurriculumCourse update(int semesterIndex, UUID prerequisiteCourseId, Instant now) {
        return new CurriculumCourse(id, programId, courseId, semesterIndex, prerequisiteCourseId, rowVersion, createdAt, now);
    }

    public static final class InvalidCurriculumException extends IllegalArgumentException {
        public InvalidCurriculumException(String message) { super(message); }
    }
}
