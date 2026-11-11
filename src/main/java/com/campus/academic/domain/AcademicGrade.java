package com.campus.academic.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A graded enrollment. Letter, four-point value and pass state are frozen when recorded. */
public record AcademicGrade(UUID id, UUID enrollmentId, GradeScheme scheme,
                            double attendanceScore, double midtermScore, Double finalScore, Double projectScore,
                            double totalScore, String letterGrade, double gradePoint, boolean passed,
                            UUID recordedBy, long rowVersion, Instant createdAt, Instant updatedAt) {
    private static final Set<String> LETTERS = Set.of("A", "B", "C", "D", "F");

    public AcademicGrade {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(letterGrade, "letterGrade");
        Objects.requireNonNull(recordedBy, "recordedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        checkScore(attendanceScore, "attendanceScore");
        checkScore(midtermScore, "midtermScore");
        if (finalScore != null) checkScore(finalScore, "finalScore");
        if (projectScore != null) checkScore(projectScore, "projectScore");
        if (scheme.projectRequired() ? finalScore != null || projectScore == null : finalScore == null || projectScore != null) {
            throw new InvalidGradeException("grade components do not match the scheme");
        }
        if (totalScore < 0 || totalScore > 10) throw new InvalidGradeException("totalScore");
        if (!LETTERS.contains(letterGrade)) throw new InvalidGradeException("letterGrade");
        if (gradePoint < 0 || gradePoint > 4) throw new InvalidGradeException("gradePoint");
        if (rowVersion < 0) throw new InvalidGradeException("rowVersion");
    }

    public static AcademicGrade create(UUID id, UUID enrollmentId, GradeScheme scheme, double attendanceScore,
                                       double midtermScore, Double finalScore, Double projectScore,
                                       UUID recordedBy, Instant now) {
        return scored(id, enrollmentId, scheme, attendanceScore, midtermScore, finalScore, projectScore, recordedBy, 0, now, now);
    }

    public AcademicGrade withScores(double attendanceScore, double midtermScore, Double finalScore, Double projectScore,
                                    UUID recordedBy, Instant now) {
        return scored(id, enrollmentId, scheme, attendanceScore, midtermScore, finalScore, projectScore,
                recordedBy, rowVersion, createdAt, now);
    }

    private static AcademicGrade scored(UUID id, UUID enrollmentId, GradeScheme scheme, double attendanceScore,
                                        double midtermScore, Double finalScore, Double projectScore,
                                        UUID recordedBy, long rowVersion, Instant createdAt, Instant updatedAt) {
        double total = GradeScale.round(attendanceScore * scheme.attendanceWeight()
                + midtermScore * scheme.midtermWeight()
                + (finalScore == null ? 0 : finalScore * scheme.finalWeight())
                + (projectScore == null ? 0 : projectScore * scheme.projectWeight()));
        return new AcademicGrade(id, enrollmentId, scheme, attendanceScore, midtermScore, finalScore, projectScore,
                total, GradeScale.letter(total), GradeScale.point(total), GradeScale.passed(total),
                recordedBy, rowVersion, createdAt, updatedAt);
    }

    private static void checkScore(double value, String field) {
        if (value < 0 || value > 10) throw new InvalidGradeException(field);
    }

    public static final class InvalidGradeException extends IllegalArgumentException {
        public InvalidGradeException(String message) { super(message); }
    }
}
