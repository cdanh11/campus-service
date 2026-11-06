package com.campus.selfservice;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.campus.academic.application.*;
import com.campus.academic.domain.*;
import com.campus.finance.application.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.*;
import com.campus.student.application.StudentManagementService;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class SelfServicePortalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AcademicDeliveryService delivery;
    @Autowired AcademicCatalogService catalog;
    @Autowired EnrollmentService enrollments;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired StudentManagementService students;
    @Autowired FinanceObligationService finance;
    @Autowired ManualPaymentService payments;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;

    UUID admin, unit, faculty, student; String prefix, accountToken;

    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var administrator = account(RoleCode.ADMIN);
        admin = administrator.id();
        unit = units.create(prefix, "Self-service unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        faculty = personnel.create(prefix, "Teacher", null, null, PersonnelType.FACULTY, unit, PersonnelStatus.ACTIVE).id();
        var owner = account(RoleCode.USER);
        accountToken = tokens.accessToken(owner);
        student = students.create(prefix, "Linked Student", null, owner.id(), unit, com.campus.student.domain.StudentStatus.ACTIVE).id();
    }

    @Test void servesProfileTimetableAndEnrollmentHistoryForTheLinkedStudent() throws Exception {
        var term = activeTerm("2099-01-01", "2099-06-01");
        var first = enrol(term, "PRJ101", "Project", 3, 2, LocalTime.of(7, 30), LocalTime.of(9, 30), "A101");
        enrol(term, "DB101", "Database", 4, 4, LocalTime.of(13, 0), LocalTime.of(15, 0), "B202");

        mvc.perform(get("/api/v1/me/student").header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentNumber").value(prefix))
                .andExpect(jsonPath("$.fullName").value("Linked Student"))
                .andExpect(jsonPath("$.organizationUnitName").value("Self-service unit"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/api/v1/me/timetable").header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(student.toString()))
                .andExpect(jsonPath("$.term.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meetings.length()").value(2))
                .andExpect(jsonPath("$.meetings[0].sectionId").value(first.toString()))
                .andExpect(jsonPath("$.meetings[0].course.code").value("PRJ101"))
                .andExpect(jsonPath("$.meetings[0].facultyName").value("Teacher"))
                .andExpect(jsonPath("$.meetings[0].schedule.weekday").value(2))
                .andExpect(jsonPath("$.meetings[0].schedule.room").value("A101"));

        mvc.perform(get("/api/v1/me/timetable").param("termId", term.toString()).header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meetings.length()").value(2));
        mvc.perform(get("/api/v1/me/enrollments").header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].course.code").value("DB101"))
                .andExpect(jsonPath("$.items[1].sectionId").value(first.toString()));
    }

    @Test void rejectsUnlinkedAccountsAnonymousCallsAndUnknownTerms() throws Exception {
        for (String path : List.of("/api/v1/me/student", "/api/v1/me/timetable", "/api/v1/me/enrollments", "/api/v1/me/finance")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        var unlinked = tokens.accessToken(account(RoleCode.USER));
        for (String path : List.of("/api/v1/me/student", "/api/v1/me/timetable", "/api/v1/me/enrollments", "/api/v1/me/finance")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + unlinked))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
        }
        mvc.perform(get("/api/v1/me/timetable").param("termId", UUID.randomUUID().toString()).header("Authorization", token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_NOT_FOUND"));
    }

    @Test void summarisesChargesPaymentsAndOverdueBalance() throws Exception {
        var today = LocalDate.now();
        var fee = finance.createFee(admin, prefix + "FEE", "Tuition", new BigDecimal("5000000"));
        var open = finance.createCharge(admin, prefix + "C1", student, fee.id(), today.minusDays(2));
        finance.createCharge(admin, prefix + "C2", student, fee.id(), today.plusDays(10));
        payments.record(admin, prefix + "R1", open.id(), new BigDecimal("2000000"), 0);
        var cancelled = finance.createCharge(admin, prefix + "C3", student, fee.id(), today.plusDays(20));
        finance.cancelCharge(admin, cancelled.id(), 0);

        mvc.perform(get("/api/v1/me/finance").header("Authorization", token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.totalCharged").value(10000000))
                .andExpect(jsonPath("$.totalPaid").value(2000000))
                .andExpect(jsonPath("$.outstanding").value(8000000))
                .andExpect(jsonPath("$.charges.length()").value(3))
                .andExpect(jsonPath("$.charges[0].id").value(open.id().toString()))
                .andExpect(jsonPath("$.charges[0].overdue").value(true))
                .andExpect(jsonPath("$.charges[0].paid").value(2000000))
                .andExpect(jsonPath("$.charges[0].outstanding").value(3000000))
                .andExpect(jsonPath("$.charges[0].payments[0].amount").value(2000000))
                .andExpect(jsonPath("$.charges[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$.charges[2].outstanding").value(0));
    }

    private UUID activeTerm(String start, String end) {
        var term = delivery.createTerm(prefix + UUID.randomUUID().toString().substring(0, 4), "Term", LocalDate.parse(start), LocalDate.parse(end));
        return delivery.updateTerm(term.id(), term.code(), term.name(), term.startDate(), term.endDate(), AcademicTermStatus.ACTIVE, 0).id();
    }

    private UUID enrol(UUID term, String course, String title, int credits, int weekday, LocalTime start, LocalTime end, String room) {
        var value = catalog.createCourse(course, title, credits, unit, AcademicCatalogStatus.ACTIVE);
        var offering = delivery.createOffering(term, value.id());
        delivery.updateOffering(offering.id(), AcademicDeliveryStatus.OPEN, 0);
        var section = delivery.createSection(offering.id(), prefix + course + UUID.randomUUID().toString().substring(0, 3), 30, faculty, null);
        var open = delivery.updateSection(section.id(), section.code(), section.capacity(), faculty,
                SectionSchedule.of(weekday, start, end, room), AcademicDeliveryStatus.OPEN, 0);
        return enrollments.create(student, open.id()).sectionId();
    }

    private UserAccount account(RoleCode role) {
        return accounts.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Self-service user",
                passwords.encode("test-password"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }

    private String token() { return "Bearer " + accountToken; }
}
