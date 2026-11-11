package com.campus.student.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import com.campus.academic.domain.AcademicCatalogStatus;
import com.campus.academic.domain.AcademicProgramRepository;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.shared.application.PeopleRegistryAudit;
import com.campus.student.domain.Student;
import com.campus.student.domain.StudentContactProfile;
import com.campus.student.domain.StudentContactProfileRepository;
import com.campus.student.domain.StudentRepository;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentProfileService {
    private final StudentAccountDirectory accounts;
    private final StudentRepository students;
    private final StudentContactProfileRepository contacts;
    private final OrganizationUnitManagementService organizations;
    private final AcademicProgramRepository programs;
    private final PeopleRegistryAudit audits;
    private final Clock clock;

    public StudentProfileService(StudentAccountDirectory accounts, StudentRepository students,
                                 StudentContactProfileRepository contacts, OrganizationUnitManagementService organizations,
                                 AcademicProgramRepository programs, PeopleRegistryAudit audits, Clock clock) {
        this.accounts = accounts;
        this.students = students;
        this.contacts = contacts;
        this.organizations = organizations;
        this.programs = programs;
        this.audits = audits;
        this.clock = clock;
    }

    public StudentProfile profile(UUID account) {
        var linked = accounts.findByAccount(account).orElseThrow(StudentProfileNotFoundException::new);
        var student = students.findById(linked.studentId()).orElseThrow(StudentProfileNotFoundException::new);
        return view(student, contacts.findByStudentId(student.id()).orElse(null));
    }

    public StudentProfile academicProfile(UUID studentId) {
        var student = students.findById(studentId).orElseThrow(StudentManagementService.StudentNotFoundException::new);
        return view(student, contacts.findByStudentId(student.id()).orElse(null));
    }

    @Transactional
    public StudentProfile updateAcademicProfile(UUID actor, UUID studentId, String cohort, UUID programId, long expectedVersion) {
        if (actor == null) throw new RequestValidationException();
        String normalizedCohort = cohort == null || cohort.isBlank() ? null
                : cohort.trim().toUpperCase(java.util.Locale.ROOT);
        var student = students.findById(studentId).orElseThrow(StudentManagementService.StudentNotFoundException::new);
        if (programId != null && programs.findById(programId)
                .filter(program -> program.status() == AcademicCatalogStatus.ACTIVE).isEmpty()) {
            throw new ProgramUnavailableException();
        }
        var current = contacts.findByStudentId(studentId).orElse(null);
        long currentVersion = current == null ? 0 : current.rowVersion();
        if (currentVersion != expectedVersion) throw new ConcurrentProfileModificationException();
        StudentContactProfile base = current == null ? StudentContactProfile.empty(studentId, clock.instant()) : current;
        StudentContactProfile saved;
        try {
            saved = contacts.saveAcademicProfile(base.updateAcademicProfile(normalizedCohort, programId, clock.instant()), expectedVersion);
        } catch (StudentContactProfile.InvalidStudentContactException exception) {
            throw new RequestValidationException();
        }
        audits.record(actor, "STUDENT", studentId, "UPDATED", clock.instant());
        return view(student, saved);
    }

    @Transactional
    public StudentProfile updateContact(UUID account, String email, String phone, String address, long expectedVersion) {
        var linked = accounts.findByAccount(account).orElseThrow(StudentProfileNotFoundException::new);
        var student = students.findById(linked.studentId()).orElseThrow(StudentProfileNotFoundException::new);
        var current = contacts.findByStudentId(student.id()).orElse(null);
        long currentVersion = current == null ? 0 : current.rowVersion();
        if (currentVersion != expectedVersion) throw new ConcurrentProfileModificationException();
        Student updatedStudent = student;
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Objects.equals(student.email(), normalizedEmail)) {
            try {
                updatedStudent = students.saveMutation(new Student(student.id(), student.studentNumber(), student.fullName(),
                        normalizedEmail, student.identityUserId(), student.organizationUnitId(), student.status(),
                        student.rowVersion(), student.createdAt(), clock.instant()), student.rowVersion());
            } catch (Student.InvalidStudentException exception) {
                throw new RequestValidationException();
            } catch (StudentManagementService.ConcurrentStudentModificationException exception) {
                throw new ConcurrentProfileModificationException();
            }
        }
        StudentContactProfile base = current == null ? StudentContactProfile.empty(student.id(), clock.instant()) : current;
        StudentContactProfile saved;
        try {
            saved = contacts.save(base.updateContact(phone, address, clock.instant()), expectedVersion);
        } catch (StudentContactProfile.InvalidStudentContactException exception) {
            throw new RequestValidationException();
        }
        audits.record(account, "STUDENT", student.id(), "UPDATED", clock.instant());
        return view(updatedStudent, saved);
    }

    private StudentProfile view(Student student, StudentContactProfile contact) {
        String unitName = organizations.get(student.organizationUnitId()).name();
        UUID programId = contact == null ? null : contact.programId();
        String programName = programId == null ? null : programs.findById(programId).map(program -> program.name()).orElse(null);
        return new StudentProfile(student.id(), student.studentNumber(), student.fullName(), student.email(),
                student.organizationUnitId(), unitName, student.status(),
                contact == null ? null : contact.phone(), contact == null ? null : contact.address(),
                contact == null ? null : contact.dateOfBirth(), contact == null ? null : contact.cohort(),
                programId, programName, contact == null ? 0 : contact.rowVersion());
    }

    public record StudentProfile(UUID id, String studentNumber, String fullName, String email,
                                 UUID organizationUnitId, String organizationUnitName, StudentStatus status,
                                 String phone, String address, LocalDate dateOfBirth, String cohort,
                                 UUID programId, String programName, long profileVersion) { }

    public static final class ConcurrentProfileModificationException extends RuntimeException { }
    public static final class RequestValidationException extends RuntimeException { }
    public static final class ProgramUnavailableException extends RuntimeException { }
}
