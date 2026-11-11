package com.campus.academic.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import com.campus.academic.domain.*;
import com.campus.shared.application.time.BusinessCalendar;
import com.campus.student.application.StudentAccountDirectory;
import com.campus.student.application.StudentProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentAcademicService {
    private final StudentAccountDirectory accounts;
    private final EnrollmentViewAssembler assembler;
    private final AcademicTermRepository terms;
    private final BusinessCalendar calendar;

    public StudentAcademicService(StudentAccountDirectory accounts, EnrollmentViewAssembler assembler,
                                  AcademicTermRepository terms, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.assembler = assembler;
        this.terms = terms;
        this.calendar = calendar;
    }

    public Timetable timetable(UUID account, UUID termId) {
        UUID studentId = student(account);
        var enrolled = assembler.views(studentId, EnrollmentStatus.ENROLLED);
        TermView term = termId == null ? current(enrolled) : EnrollmentViewAssembler.termView(terms.findById(termId)
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
        var items = assembler.views(studentId, null).stream()
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

    public record TermView(UUID id, String code, String name, LocalDate startDate, LocalDate endDate, AcademicTermStatus status) { }
    public record CourseView(UUID id, String code, String title, int credits) { }
    public record EnrollmentView(UUID enrollmentId, EnrollmentStatus status, Instant enrolledAt, TermView term, CourseView course,
                                 UUID sectionId, String sectionCode, UUID facultyId, String facultyName,
                                 SectionSchedule schedule, AcademicDeliveryStatus sectionStatus) { }
    public record Timetable(UUID studentId, TermView term, List<EnrollmentView> meetings) { }
    public record Enrollments(UUID studentId, List<EnrollmentView> items) { }
}
