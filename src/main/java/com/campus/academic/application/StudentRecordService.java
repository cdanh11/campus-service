package com.campus.academic.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.campus.academic.domain.*;
import com.campus.student.application.StudentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.campus.academic.application.StudentAcademicService.CourseView;
import com.campus.academic.application.StudentAcademicService.EnrollmentView;
import com.campus.academic.application.StudentAcademicService.TermView;

/** Read model for the student transcript, GPA summaries and curriculum progress. */
@Service
@Transactional(readOnly = true)
public class StudentRecordService {
    private final StudentProfileService profiles;
    private final EnrollmentViewAssembler assembler;
    private final AcademicGradeRepository grades;
    private final AcademicCourseRepository courses;
    private final CurriculumCourseRepository curriculum;
    private final AcademicProgramRepository programs;

    public StudentRecordService(StudentProfileService profiles, EnrollmentViewAssembler assembler,
                                AcademicGradeRepository grades, AcademicCourseRepository courses,
                                CurriculumCourseRepository curriculum, AcademicProgramRepository programs) {
        this.profiles = profiles;
        this.assembler = assembler;
        this.grades = grades;
        this.courses = courses;
        this.curriculum = curriculum;
        this.programs = programs;
    }

    public Transcript transcript(UUID account) {
        var student = profiles.profile(account);
        var views = assembler.views(student.id(), null);
        var gradeByEnrollment = new HashMap<UUID, AcademicGrade>();
        for (var grade : grades.findAllByEnrollmentIds(views.stream().map(EnrollmentView::enrollmentId).toList())) {
            gradeByEnrollment.put(grade.enrollmentId(), grade);
        }
        var attempts = new ArrayList<Attempt>();
        for (var view : views) {
            var grade = gradeByEnrollment.get(view.enrollmentId());
            if (grade != null) attempts.add(new Attempt(view, grade));
        }
        var courseById = courses.findAllById(attempts.stream().map(attempt -> attempt.view().course().id()).distinct().toList())
                .stream().collect(Collectors.toMap(AcademicCourse::id, value -> value));
        var flags = retakeFlags(attempts);
        var termGroups = termGroups(attempts);
        var bestByCourse = bestByCourse(attempts);
        var summary = summary(bestByCourse, termGroups);

        attempts.sort(Comparator.comparing((Attempt attempt) -> attempt.view().term().startDate()).reversed()
                .thenComparing(attempt -> attempt.view().term().code())
                .thenComparing(attempt -> attempt.view().course().code()));
        var records = attempts.stream().map(attempt -> {
            var grade = attempt.grade();
            var scheme = grade.scheme();
            var view = attempt.view();
            var flag = flags.getOrDefault(view.enrollmentId(), RetakeFlags.NONE);
            var course = courseById.get(view.course().id());
            return new TranscriptRecord(view.enrollmentId(), view.term(), view.course(), view.sectionCode(),
                    course == null ? null : course.courseGroup().name(), scheme.name(),
                    scheme.attendanceWeight(), scheme.midtermWeight(), scheme.finalWeight(), scheme.projectWeight(),
                    grade.attendanceScore(), grade.midtermScore(), grade.finalScore(), grade.projectScore(),
                    grade.totalScore(), grade.letterGrade(), grade.gradePoint(), grade.passed(),
                    flag.retake(), flag.improvement(), grade.updatedAt());
        }).toList();

        var terms = termGroups.stream().map(group -> new TranscriptTerm(group.term().id(), group.term().code(),
                group.term().name(), group.term().startDate(), group.term().endDate(), group.gpa(), group.credits(),
                group.passedCredits(), group.attempts(), group.credits() == 0 ? null : GradeClassification.of(group.gpa()).name())).toList();
        return new Transcript(student.id(), student.programId(), student.programName(), student.cohort(),
                terms, summary, records);
    }

    public Curriculum curriculum(UUID account) {
        var student = profiles.profile(account);
        if (student.programId() == null) throw new CurriculumNotAvailableException();
        var program = programs.findById(student.programId()).orElseThrow(CurriculumNotAvailableException::new);
        var entries = curriculum.findByProgramId(program.id());
        if (entries.isEmpty()) return new Curriculum(student.id(), program.id(), program.name(), student.cohort(), List.of());

        var views = assembler.views(student.id(), null);
        var gradeByEnrollment = new HashMap<UUID, AcademicGrade>();
        for (var grade : grades.findAllByEnrollmentIds(views.stream().map(EnrollmentView::enrollmentId).toList())) {
            gradeByEnrollment.put(grade.enrollmentId(), grade);
        }
        var attemptsByCourse = new HashMap<UUID, List<Attempt>>();
        var inProgress = new HashSet<UUID>();
        for (var view : views) {
            var grade = gradeByEnrollment.get(view.enrollmentId());
            if (grade != null) {
                attemptsByCourse.computeIfAbsent(view.course().id(), key -> new ArrayList<>()).add(new Attempt(view, grade));
            } else if (view.status() == EnrollmentStatus.ENROLLED) {
                inProgress.add(view.course().id());
            }
        }
        var courseById = courses.findAllById(entries.stream()
                        .flatMap(entry -> Stream.of(entry.courseId(), entry.prerequisiteCourseId()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(AcademicCourse::id, value -> value));
        var bestByCourse = bestByCourse(attemptsByCourse.values().stream().flatMap(List::stream).toList());

        var semesterMap = new TreeMap<Integer, List<CurriculumCourseStatus>>();
        for (var entry : entries) {
            var course = courseById.get(entry.courseId());
            if (course == null) continue;
            var best = bestByCourse.get(course.id());
            var prerequisite = entry.prerequisiteCourseId() == null ? null : courseById.get(entry.prerequisiteCourseId());
            var prerequisiteBest = prerequisite == null ? null : bestByCourse.get(prerequisite.id());
            String status = best == null ? (inProgress.contains(course.id()) ? "IN_PROGRESS" : "NOT_STARTED")
                    : best.grade().passed() ? "PASSED" : "FAILED";
            var attempts = attemptsByCourse.getOrDefault(course.id(), List.of());
            semesterMap.computeIfAbsent(entry.semesterIndex(), key -> new ArrayList<>()).add(new CurriculumCourseStatus(
                    course.id(), course.code(), course.title(), course.credits(), course.courseGroup().name(), status,
                    best == null ? null : best.grade().totalScore(), best == null ? null : best.grade().letterGrade(),
                    attempts.size() > 1, inProgress.contains(course.id()),
                    prerequisite == null ? null : prerequisite.id(), prerequisite == null ? null : prerequisite.code(),
                    prerequisite == null ? null : prerequisite.title(),
                    prerequisiteBest != null && prerequisiteBest.grade().passed()));
        }
        var semesters = semesterMap.entrySet().stream().map(entry -> new CurriculumSemester(entry.getKey(),
                entry.getValue().stream().sorted(Comparator.comparing(CurriculumCourseStatus::code)).toList())).toList();
        return new Curriculum(student.id(), program.id(), program.name(), student.cohort(), semesters);
    }

    private Map<UUID, RetakeFlags> retakeFlags(List<Attempt> attempts) {
        var result = new HashMap<UUID, RetakeFlags>();
        var byCourse = new HashMap<UUID, List<Attempt>>();
        for (var attempt : attempts) byCourse.computeIfAbsent(attempt.view().course().id(), key -> new ArrayList<>()).add(attempt);
        for (var values : byCourse.values()) {
            values.sort(Comparator.comparing(attempt -> attempt.view().term().startDate()));
            boolean earlierGraded = false, earlierPassed = false;
            for (var attempt : values) {
                result.put(attempt.view().enrollmentId(), new RetakeFlags(earlierGraded, earlierPassed));
                earlierGraded = true;
                earlierPassed = earlierPassed || attempt.grade().passed();
            }
        }
        return result;
    }

    private List<TermGroup> termGroups(List<Attempt> attempts) {
        var groups = new LinkedHashMap<UUID, TermGroup>();
        for (var attempt : attempts) {
            var term = attempt.view().term();
            groups.computeIfAbsent(term.id(), key -> new TermGroup(term)).add(attempt);
        }
        return groups.values().stream().sorted(Comparator.comparing((TermGroup group) -> group.term().startDate()).reversed())
                .toList();
    }

    private Map<UUID, Attempt> bestByCourse(List<Attempt> attempts) {
        var result = new HashMap<UUID, Attempt>();
        for (var attempt : attempts) {
            result.merge(attempt.view().course().id(), attempt, (left, right) -> better(left, right) ? left : right);
        }
        return result;
    }

    private static boolean better(Attempt left, Attempt right) {
        if (left.grade().gradePoint() != right.grade().gradePoint()) return left.grade().gradePoint() > right.grade().gradePoint();
        if (left.grade().totalScore() != right.grade().totalScore()) return left.grade().totalScore() > right.grade().totalScore();
        return left.view().term().startDate().isBefore(right.view().term().startDate());
    }

    private Summary summary(Map<UUID, Attempt> bestByCourse, List<TermGroup> terms) {
        double credits = 0, points = 0;
        int passedCredits = 0;
        for (var attempt : bestByCourse.values()) {
            int value = attempt.view().course().credits();
            credits += value;
            points += attempt.grade().gradePoint() * value;
            if (attempt.grade().passed()) passedCredits += value;
        }
        double gpa = credits == 0 ? 0 : GradeScale.round(points / credits);
        TermGroup latest = terms.isEmpty() ? null : terms.get(0);
        return new Summary(gpa, (int) credits, bestByCourse.size(), credits == 0 ? null : GradeClassification.of(gpa).name(),
                latest == null ? null : latest.term().id(), latest == null ? 0 : latest.gpa(),
                latest == null ? 0 : latest.passedCredits(),
                latest == null || latest.credits() == 0 ? null : GradeClassification.of(latest.gpa()).name());
    }

    private record Attempt(EnrollmentView view, AcademicGrade grade) { }
    private record RetakeFlags(boolean retake, boolean improvement) {
        static final RetakeFlags NONE = new RetakeFlags(false, false);
    }

    private static final class TermGroup {
        private final TermView term;
        private final List<Attempt> attempts = new ArrayList<>();
        private TermGroup(TermView term) { this.term = term; }
        private void add(Attempt attempt) { attempts.add(attempt); }
        private TermView term() { return term; }
        private int attempts() { return attempts.size(); }
        private int credits() { return attempts.stream().mapToInt(attempt -> attempt.view().course().credits()).sum(); }
        private int passedCredits() {
            return attempts.stream().filter(attempt -> attempt.grade().passed())
                    .mapToInt(attempt -> attempt.view().course().credits()).sum();
        }
        private double gpa() {
            int credits = credits();
            if (credits == 0) return 0;
            double points = attempts.stream().mapToDouble(attempt -> attempt.grade().gradePoint() * attempt.view().course().credits()).sum();
            return GradeScale.round(points / credits);
        }
    }

    public record Transcript(UUID studentId, UUID programId, String programName, String cohort,
                             List<TranscriptTerm> terms, Summary summary, List<TranscriptRecord> records) { }
    public record TranscriptTerm(UUID termId, String code, String name, LocalDate startDate, LocalDate endDate,
                                 double gpa, int credits, int passedCredits, int gradedCourses, String classification) { }
    public record Summary(double cumulativeGpa, int cumulativeCredits, int gradedCourses, String classification,
                          UUID latestTermId, double latestTermGpa, int latestTermPassedCredits, String latestTermClassification) { }
    public record TranscriptRecord(UUID enrollmentId, TermView term, CourseView course, String sectionCode,
                                   String courseGroup, String scheme,
                                   double attendanceWeight, double midtermWeight, double finalWeight, double projectWeight,
                                   Double attendanceScore, Double midtermScore, Double finalScore, Double projectScore,
                                   double totalScore, String letterGrade, double gradePoint, boolean passed,
                                   boolean retake, boolean improvement, Instant gradedAt) { }
    public record Curriculum(UUID studentId, UUID programId, String programName, String cohort,
                             List<CurriculumSemester> semesters) { }
    public record CurriculumSemester(int index, List<CurriculumCourseStatus> courses) { }
    public record CurriculumCourseStatus(UUID courseId, String code, String title, int credits, String courseGroup,
                                         String status, Double bestTotal, String bestLetter, boolean improvementAttempted,
                                         boolean inProgress, UUID prerequisiteCourseId, String prerequisiteCode,
                                         String prerequisiteTitle, boolean prerequisiteSatisfied) { }

    public static final class CurriculumNotAvailableException extends RuntimeException { }
}
