package com.campus.student.application;

import java.util.UUID;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.student.domain.StudentRepository;
import com.campus.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentProfileService {
    private final StudentAccountDirectory accounts;
    private final StudentRepository students;
    private final OrganizationUnitManagementService organizations;

    public StudentProfileService(StudentAccountDirectory accounts, StudentRepository students,
                                 OrganizationUnitManagementService organizations) {
        this.accounts = accounts;
        this.students = students;
        this.organizations = organizations;
    }

    public StudentProfile profile(UUID account) {
        var linked = accounts.findByAccount(account).orElseThrow(StudentProfileNotFoundException::new);
        var student = students.findById(linked.studentId()).orElseThrow(StudentProfileNotFoundException::new);
        String unitName = organizations.get(student.organizationUnitId()).name();
        return new StudentProfile(student.id(), student.studentNumber(), student.fullName(), student.email(),
                student.organizationUnitId(), unitName, student.status());
    }

    public record StudentProfile(UUID id, String studentNumber, String fullName, String email,
                                 UUID organizationUnitId, String organizationUnitName, StudentStatus status) { }
}
