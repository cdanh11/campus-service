package com.campus.academic.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AcademicRecordsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void convertsTotalsToLettersPointsAndPassStateAtEveryBoundary() {
        assertThat(letterAndPoint(10.0)).isEqualTo("A/4.0");
        assertThat(letterAndPoint(8.5)).isEqualTo("A/4.0");
        assertThat(letterAndPoint(8.49)).isEqualTo("A/4.0");
        assertThat(letterAndPoint(8.45)).isEqualTo("A/4.0");
        assertThat(letterAndPoint(8.44)).isEqualTo("B/3.0");
        assertThat(letterAndPoint(7.0)).isEqualTo("B/3.0");
        assertThat(letterAndPoint(6.9)).isEqualTo("C/2.0");
        assertThat(letterAndPoint(5.5)).isEqualTo("C/2.0");
        assertThat(letterAndPoint(5.4)).isEqualTo("D/1.0");
        assertThat(letterAndPoint(4.1)).isEqualTo("D/1.0");
        assertThat(letterAndPoint(4.05)).isEqualTo("D/1.0");
        assertThat(letterAndPoint(4.04)).isEqualTo("F/0.0");
        assertThat(letterAndPoint(4.0)).isEqualTo("F/0.0");
        assertThat(letterAndPoint(3.99)).isEqualTo("F/0.0");
        assertThat(GradeScale.passed(4.05)).isTrue();
        assertThat(GradeScale.passed(4.04)).isFalse();
        assertThat(GradeScale.round(7.345)).isEqualTo(7.35);
        assertThat(GradeScale.round(7.344)).isEqualTo(7.34);
    }

    @Test
    void classifiesGpaOnTheFourPointScale() {
        assertThat(GradeClassification.of(3.6)).isEqualTo(GradeClassification.XUAT_SAC);
        assertThat(GradeClassification.of(3.59)).isEqualTo(GradeClassification.GIOI);
        assertThat(GradeClassification.of(3.2)).isEqualTo(GradeClassification.GIOI);
        assertThat(GradeClassification.of(2.5)).isEqualTo(GradeClassification.KHA);
        assertThat(GradeClassification.of(2.0)).isEqualTo(GradeClassification.TRUNG_BINH);
        assertThat(GradeClassification.of(1.0)).isEqualTo(GradeClassification.YEU);
        assertThat(GradeClassification.of(0.99)).isEqualTo(GradeClassification.KEM);
    }

    @Test
    void derivesSchemeFromCourseGroupAndFinalProjectFlag() {
        assertThat(GradeScheme.of(CourseGroup.GENERAL, true)).isEqualTo(GradeScheme.GENERAL);
        assertThat(GradeScheme.of(CourseGroup.GENERAL, false)).isEqualTo(GradeScheme.GENERAL);
        assertThat(GradeScheme.of(CourseGroup.MAJOR, true)).isEqualTo(GradeScheme.MAJOR_PROJECT);
        assertThat(GradeScheme.of(CourseGroup.MAJOR, false)).isEqualTo(GradeScheme.MAJOR_FINAL);
        assertThat(GradeScheme.of(CourseGroup.SPECIALIZED, true)).isEqualTo(GradeScheme.MAJOR_PROJECT);
        assertThat(GradeScheme.of(CourseGroup.SPECIALIZED, false)).isEqualTo(GradeScheme.MAJOR_FINAL);
    }

    @Test
    void weightsEachSchemeAndSnapshotsTheResult() {
        var general = AcademicGrade.create(UUID.randomUUID(), UUID.randomUUID(), GradeScheme.GENERAL,
                8.0, 7.0, 9.0, null, UUID.randomUUID(), NOW);
        assertThat(general.totalScore()).isEqualTo(8.1);
        assertThat(general.letterGrade()).isEqualTo("B");
        assertThat(general.gradePoint()).isEqualTo(3.0);
        assertThat(general.passed()).isTrue();
        assertThat(general.finalScore()).isEqualTo(9.0);
        assertThat(general.projectScore()).isNull();

        var project = AcademicGrade.create(UUID.randomUUID(), UUID.randomUUID(), GradeScheme.MAJOR_PROJECT,
                9.0, 8.0, null, 8.0, UUID.randomUUID(), NOW);
        assertThat(project.totalScore()).isEqualTo(8.1);

        var majorFinal = AcademicGrade.create(UUID.randomUUID(), UUID.randomUUID(), GradeScheme.MAJOR_FINAL,
                8.0, 6.0, 9.0, null, UUID.randomUUID(), NOW);
        assertThat(majorFinal.totalScore()).isEqualTo(8.0);
        assertThat(majorFinal.gradePoint()).isEqualTo(3.0);
    }

    @Test
    void correctionKeepsIdentityAndCreationTimeButRecomputesScores() {
        var original = AcademicGrade.create(UUID.randomUUID(), UUID.randomUUID(), GradeScheme.MAJOR_FINAL,
                5.0, 5.0, 5.0, null, UUID.randomUUID(), NOW);
        var corrected = original.withScores(9.0, 9.0, 9.0, null, UUID.randomUUID(), NOW.plusSeconds(60));
        assertThat(corrected.id()).isEqualTo(original.id());
        assertThat(corrected.enrollmentId()).isEqualTo(original.enrollmentId());
        assertThat(corrected.createdAt()).isEqualTo(original.createdAt());
        assertThat(corrected.totalScore()).isEqualTo(9.0);
        assertThat(corrected.letterGrade()).isEqualTo("A");
    }

    @Test
    void rejectsOutOfRangeScoresAndComponentsThatDoNotMatchTheScheme() {
        var id = UUID.randomUUID();
        var enrollment = UUID.randomUUID();
        var actor = UUID.randomUUID();
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.GENERAL,
                -0.1, 5.0, 5.0, null, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.GENERAL,
                5.0, 10.1, 5.0, null, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.GENERAL,
                5.0, 5.0, null, null, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.GENERAL,
                5.0, 5.0, 5.0, 5.0, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.MAJOR_PROJECT,
                5.0, 5.0, 5.0, 5.0, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
        assertThatThrownBy(() -> AcademicGrade.create(id, enrollment, GradeScheme.MAJOR_PROJECT,
                5.0, 5.0, null, null, actor, NOW)).isInstanceOf(AcademicGrade.InvalidGradeException.class);
    }

    @Test
    void curriculumEntryValidatesSemesterBoundsAndSelfPrerequisites() {
        var program = UUID.randomUUID();
        var course = UUID.randomUUID();
        assertThat(CurriculumCourse.create(UUID.randomUUID(), program, course, 1, null, NOW).semesterIndex()).isEqualTo(1);
        assertThat(CurriculumCourse.create(UUID.randomUUID(), program, course, 12, UUID.randomUUID(), NOW).semesterIndex()).isEqualTo(12);
        assertThatThrownBy(() -> CurriculumCourse.create(UUID.randomUUID(), program, course, 0, null, NOW))
                .isInstanceOf(CurriculumCourse.InvalidCurriculumException.class);
        assertThatThrownBy(() -> CurriculumCourse.create(UUID.randomUUID(), program, course, 13, null, NOW))
                .isInstanceOf(CurriculumCourse.InvalidCurriculumException.class);
        assertThatThrownBy(() -> CurriculumCourse.create(UUID.randomUUID(), program, course, 1, course, NOW))
                .isInstanceOf(CurriculumCourse.InvalidCurriculumException.class);
    }

    private static String letterAndPoint(double total) {
        return GradeScale.letter(total) + "/" + GradeScale.point(total);
    }
}
