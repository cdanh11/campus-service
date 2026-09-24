package com.campus.shared.infrastructure.persistence;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.PersonnelStatus;
import com.campus.personnel.domain.PersonnelType;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PeopleRegistryAuditIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired OrganizationUnitManagementService organizations;
    @Autowired StudentManagementService students;
    @Autowired FacultyStaffManagementService facultyStaff;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recordsStudentAndFacultyStaffMutationsWithTheAuthenticatedActor() {
        UUID actorId = actor("people-audit@campus.example").id();
        UUID unitId = organization("AUD").id();

        var student = students.create(actorId, "S-AUD-1", "Student Audit", null, null, unitId, StudentStatus.ACTIVE);
        students.update(actorId, student.id(), "S-AUD-1", "Student Updated", null, null, unitId, StudentStatus.INACTIVE, student.rowVersion());
        var member = facultyStaff.create(actorId, "P-AUD-1", "Faculty Audit", null, null, PersonnelType.FACULTY, unitId, PersonnelStatus.ACTIVE);
        facultyStaff.update(actorId, member.id(), "P-AUD-1", "Faculty Updated", null, null, PersonnelType.STAFF, unitId, PersonnelStatus.INACTIVE, member.rowVersion());

        assertThat(count(actorId, student.id(), "STUDENT", "CREATED")).isEqualTo(1);
        assertThat(count(actorId, student.id(), "STUDENT", "UPDATED")).isEqualTo(1);
        assertThat(count(actorId, member.id(), "FACULTY_STAFF", "CREATED")).isEqualTo(1);
        assertThat(count(actorId, member.id(), "FACULTY_STAFF", "UPDATED")).isEqualTo(1);
    }

    @Test
    void rollsBackTheMutationWhenAuditPersistenceFails() {
        UUID unitId = organization("RBK").id();
        UUID missingActor = UUID.randomUUID();

        assertThatThrownBy(() -> students.create(missingActor, "S-ROLLBACK", "Rollback Student", null, null, unitId, StudentStatus.ACTIVE))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM students WHERE student_number = 'S-ROLLBACK'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM people_registry_audit_events WHERE target_id IN (SELECT id FROM students WHERE student_number = 'S-ROLLBACK')", Long.class)).isZero();
    }

    private UserAccount actor(String email) {
        return users.save(UserAccount.create(UUID.randomUUID(), email, email, passwords.encode("valid-password"), AccountStatus.ACTIVE,
                Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), Instant.now()));
    }

    private com.campus.organization.domain.OrganizationUnit organization(String code) {
        return organizations.create(code, "Audit " + code, OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE);
    }

    private long count(UUID actorId, UUID targetId, String resourceType, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM people_registry_audit_events WHERE actor_user_id = ? AND target_id = ? AND resource_type = ? AND action = ?",
                Long.class, actorId, targetId, resourceType, action);
    }
}
