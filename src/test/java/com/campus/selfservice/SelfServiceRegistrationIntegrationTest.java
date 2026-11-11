package com.campus.selfservice;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.campus.academic.application.AcademicAdministrationService;
import com.campus.academic.application.AcademicCatalogService;
import com.campus.academic.application.AcademicDeliveryService;
import com.campus.academic.application.AcademicGradeService;
import com.campus.academic.application.EnrollmentService;
import com.campus.academic.application.RegistrationService;
import com.campus.academic.domain.AcademicCatalogStatus;
import com.campus.academic.domain.AcademicDeliveryStatus;
import com.campus.academic.domain.AcademicTerm;
import com.campus.academic.domain.AcademicTermStatus;
import com.campus.academic.domain.SectionSchedule;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.OrganizationUnitStatus;
import com.campus.organization.domain.OrganizationUnitType;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.PersonnelStatus;
import com.campus.personnel.domain.PersonnelType;
import com.campus.student.application.StudentManagementService;
import com.campus.student.application.StudentProfileService;
import com.campus.student.domain.StudentStatus;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class SelfServiceRegistrationIntegrationTest {
    private static final String ROOT = "/api/v1/me/registration";

    @Autowired MockMvc mvc;
    @Autowired RegistrationService registrations;
    @Autowired EnrollmentService enrollments;
    @Autowired AcademicDeliveryService delivery;
    @Autowired AcademicCatalogService catalog;
    @Autowired AcademicAdministrationService administration;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired StudentManagementService students;
    @Autowired StudentProfileService profiles;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;

    UUID admin, unit, faculty;
    String prefix, adminToken;

    @BeforeEach void setup() {
        jdbc.execute("delete from academic_grades");
        jdbc.execute("delete from academic_enrollments");
        jdbc.execute("delete from academic_class_sections");
        jdbc.execute("delete from academic_course_offerings");
        jdbc.execute("delete from academic_terms");
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var administrator = account(RoleCode.ADMIN);
        admin = administrator.id();
        adminToken = "Bearer " + tokens.accessToken(administrator);
        unit = units.create(prefix, "Registration unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        faculty = personnel.create(prefix, "Registration Teacher", null, null, PersonnelType.FACULTY, unit, PersonnelStatus.ACTIVE).id();
    }

    @Test void protectsRoutesAndRequiresALinkedStudentProfile() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(ROOT + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        var unlinked = "Bearer " + tokens.accessToken(account(RoleCode.USER));
        mvc.perform(get(ROOT).header("Authorization", unlinked)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
    }

    @Test void staggersCohortSlotsAndOnlyLetsTheCurrentCohortRegister() throws Exception {
        var now = Instant.now();
        var term = term(now.minusSeconds(30), now.plus(Duration.ofDays(14)));
        var section = section(term, "PRJ101", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var young = learner("K23", null);
        var elder = learner("K21", null);

        mvc.perform(get(ROOT).header("Authorization", young.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.cohort").value("K23"))
                .andExpect(jsonPath("$.window.state").value("NOT_OPEN"))
                .andExpect(jsonPath("$.window.slots.length()").value(6))
                .andExpect(jsonPath("$.window.slots[0].cohort").value("K21"))
                .andExpect(jsonPath("$.window.slots[0].current").value(false))
                .andExpect(jsonPath("$.window.slots[2].cohort").value("K23"))
                .andExpect(jsonPath("$.window.slots[2].current").value(true))
                .andExpect(jsonPath("$.sections[0].eligible").value(false))
                .andExpect(jsonPath("$.sections[0].blockedReason").value("NOT_OPEN"));
        mvc.perform(post(ROOT).header("Authorization", young.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", section))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_NOT_OPEN"));

        mvc.perform(get(ROOT).header("Authorization", elder.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.window.state").value("OPEN"))
                .andExpect(jsonPath("$.window.slots[0].current").value(true))
                .andExpect(jsonPath("$.sections[0].eligible").value(true))
                .andExpect(jsonPath("$.sections[0].occupied").value(0))
                .andExpect(jsonPath("$.sections[0].remaining").value(2));
        var created = mvc.perform(post(ROOT).header("Authorization", elder.authorization()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("sectionId", section))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ENROLLED")).andReturn();
        var enrollment = json.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get(ROOT).header("Authorization", elder.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].enrolled").value(true))
                .andExpect(jsonPath("$.sections[0].blockedReason").value("ALREADY_ENROLLED"))
                .andExpect(jsonPath("$.enrolled[0].sectionId").value(section.toString()))
                .andExpect(jsonPath("$.enrolled[0].course.code").value(prefix + "PRJ101"));
        mvc.perform(post(ROOT).header("Authorization", elder.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", section))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_ALREADY_EXISTS"));

        mvc.perform(delete(ROOT + "/" + enrollment).header("Authorization", young.authorization()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_NOT_FOUND"));
        mvc.perform(delete(ROOT + "/" + enrollment).header("Authorization", elder.authorization()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(ROOT + "/" + enrollment).header("Authorization", elder.authorization()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_STATE"));
        assertThat(occupied(section)).isZero();
    }

    @Test void blocksFullConflictingAndDuplicateSections() throws Exception {
        var term = term(Instant.now().minusSeconds(30), Instant.now().plus(Duration.ofDays(7)));
        var first = learner("K21", null);
        var second = learner("K21", null);
        var seat = section(term, "SEAT101", 1, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));

        assertThat(registrations.register(first.account(), seat).status()).isEqualTo(com.campus.academic.domain.EnrollmentStatus.ENROLLED);
        var denied = registrations.registration(second.account());
        assertThat(denied.sections()).singleElement().satisfies(option -> {
            assertThat(option.eligible()).isFalse();
            assertThat(option.blockedReason()).isEqualTo(RegistrationService.REASON_FULL);
            assertThat(option.remaining()).isZero();
        });
        mvc.perform(post(ROOT).header("Authorization", second.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", seat))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SECTION_CAPACITY_EXCEEDED"));

        var clash = section(term, "CLASH101", 2, schedule(2, LocalTime.of(8, 0), LocalTime.of(10, 0)));
        assertThat(registrations.registration(first.account()).sections()).filteredOn(option -> option.sectionId().equals(clash))
                .singleElement().satisfies(option -> assertThat(option.blockedReason()).isEqualTo(RegistrationService.REASON_SCHEDULE));
        mvc.perform(post(ROOT).header("Authorization", first.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", clash))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_SCHEDULE_CONFLICT"));

        var offering = offering(term, "DUP101");
        var firstSection = openSection(offering, "DUP-A", 2, schedule(3, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var secondSection = openSection(offering, "DUP-B", 2, schedule(4, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        assertThat(registrations.register(first.account(), firstSection).sectionId()).isEqualTo(firstSection);
        assertThat(registrations.registration(first.account()).sections()).filteredOn(option -> option.sectionId().equals(secondSection))
                .singleElement().satisfies(option -> assertThat(option.blockedReason()).isEqualTo(RegistrationService.REASON_ALREADY_ENROLLED));
        mvc.perform(post(ROOT).header("Authorization", first.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", secondSection))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_ALREADY_EXISTS"));
        assertThat(occupied(firstSection)).isEqualTo(1);
        assertThat(occupied(secondSection)).isZero();
    }

    @Test void enforcesCurriculumPrerequisites() throws Exception {
        var program = catalog.createProgram(prefix + "PROG", "Registration program", unit, AcademicCatalogStatus.ACTIVE);
        var prerequisite = catalog.createCourse(prefix + "PRE101", "Prerequisite course", 3, unit, AcademicCatalogStatus.ACTIVE);
        var target = catalog.createCourse(prefix + "TGT101", "Target course", 3, unit, AcademicCatalogStatus.ACTIVE);
        administration.createCurriculum(admin, program.id(), prerequisite.id(), 1, null);
        administration.createCurriculum(admin, program.id(), target.id(), 2, prerequisite.id());
        var term = term(Instant.now().minusSeconds(30), Instant.now().plus(Duration.ofDays(7)));
        var enrolledCourse = catalog.createCourse(prefix + "FREE101", "Free course", 3, unit, AcademicCatalogStatus.ACTIVE);
        var freeOffering = delivery.createOffering(term.id(), enrolledCourse.id());
        delivery.updateOffering(freeOffering.id(), AcademicDeliveryStatus.OPEN, 0);
        var section = openSection(freeOffering.id(), "TGT-A", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var targetOffering = delivery.createOffering(term.id(), target.id());
        delivery.updateOffering(targetOffering.id(), AcademicDeliveryStatus.OPEN, 0);
        var targetSection = openSection(targetOffering.id(), "TGT-B", 2, schedule(3, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var learner = learner("K21", program.id());

        assertThat(registrations.registration(learner.account()).sections()).filteredOn(option -> option.sectionId().equals(targetSection))
                .singleElement().satisfies(option -> {
                    assertThat(option.eligible()).isFalse();
                    assertThat(option.blockedReason()).isEqualTo(RegistrationService.REASON_PREREQUISITE);
                    assertThat(option.prerequisiteCourseCode()).isEqualTo(prefix + "PRE101");
                    assertThat(option.prerequisiteCourseTitle()).isEqualTo("Prerequisite course");
                });
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", targetSection))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_PREREQUISITE_NOT_MET"));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", section))))
                .andExpect(status().isCreated());
    }

    @Test void unlocksTheNextCourseAfterStudyingAFailedPrerequisite() throws Exception {
        var program = catalog.createProgram(prefix + "PROG2", "Registration program 2", unit, AcademicCatalogStatus.ACTIVE);
        var prerequisite = catalog.createCourse(prefix + "PRE201", "Prerequisite course 2", 3, unit, AcademicCatalogStatus.ACTIVE);
        var target = catalog.createCourse(prefix + "TGT201", "Target course 2", 3, unit, AcademicCatalogStatus.ACTIVE);
        administration.createCurriculum(admin, program.id(), prerequisite.id(), 1, null);
        administration.createCurriculum(admin, program.id(), target.id(), 2, prerequisite.id());
        var learner = learner("K21", program.id());

        var past = term(Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(20)));
        var prerequisiteOffering = delivery.createOffering(past.id(), prerequisite.id());
        delivery.updateOffering(prerequisiteOffering.id(), AcademicDeliveryStatus.OPEN, 0);
        var prerequisiteSection = openSection(prerequisiteOffering.id(), "PRE-A", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var attempt = enrollments.create(learner.student(), prerequisiteSection);
        administration.createGrade(admin, attempt.id(), new AcademicGradeService.GradeScores(3.0, 3.0, 0.0, null));

        var term = term(Instant.now().minusSeconds(30), Instant.now().plus(Duration.ofDays(7)));
        var targetOffering = delivery.createOffering(term.id(), target.id());
        delivery.updateOffering(targetOffering.id(), AcademicDeliveryStatus.OPEN, 0);
        var targetSection = openSection(targetOffering.id(), "TGT-B", 2, schedule(3, LocalTime.of(7, 30), LocalTime.of(9, 30)));

        assertThat(registrations.registration(learner.account()).sections()).filteredOn(option -> option.sectionId().equals(targetSection))
                .singleElement().satisfies(option -> assertThat(option.eligible()).isTrue());
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", targetSection))))
                .andExpect(status().isCreated());
    }

    @Test void reportsUnconfiguredClosedAndUpcomingWindows() throws Exception {
        var learner = learner("K21", null);
        var unconfigured = delivery.createTerm(prefix + "T0", "No window", LocalDate.now(), LocalDate.now().plusMonths(4));
        delivery.updateTerm(unconfigured.id(), unconfigured.code(), unconfigured.name(), unconfigured.startDate(),
                unconfigured.endDate(), AcademicTermStatus.ACTIVE, 0);
        mvc.perform(get(ROOT).header("Authorization", learner.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.window.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.window.slots.length()").value(0));
        var unconfiguredSection = section(delivery.term(unconfigured.id()), "NOW101", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", unconfiguredSection))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_NOT_CONFIGURED"));

        var now = Instant.now();
        var closed = term(now.minus(Duration.ofDays(10)), now.minus(Duration.ofHours(1)));
        var closedSection = section(closed, "LATE101", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        mvc.perform(get(ROOT).header("Authorization", learner.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.window.state").value("CLOSED"));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", closedSection))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));

        var upcoming = term(now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(12)));
        var upcomingSection = section(upcoming, "NEXT101", 2, schedule(4, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        mvc.perform(get(ROOT).header("Authorization", learner.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.window.state").value("NOT_OPEN"))
                .andExpect(jsonPath("$.window.slots.length()").value(6));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", upcomingSection))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_NOT_OPEN"));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content("{\"sectionId\":\"not-a-uuid\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(ROOT + "/not-a-uuid").header("Authorization", learner.authorization()))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsRegistrationWhenTheStudentHasNoCohort() throws Exception {
        var term = term(Instant.now().minusSeconds(30), Instant.now().plus(Duration.ofDays(7)));
        var section = section(term, "NOCOHORT", 2, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var learner = learner(null, null);
        mvc.perform(get(ROOT).header("Authorization", learner.authorization())).andExpect(status().isOk())
                .andExpect(jsonPath("$.window.state").value("COHORT_MISSING"))
                .andExpect(jsonPath("$.window.slots.length()").value(6));
        mvc.perform(post(ROOT).header("Authorization", learner.authorization()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sectionId", section))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_COHORT_MISSING"));
    }

    @Test void competingStudentsCannotTakeTheSameLastSeat() throws Exception {
        var term = term(Instant.now().minusSeconds(30), Instant.now().plus(Duration.ofDays(7)));
        var section = section(term, "RACE101", 1, schedule(2, LocalTime.of(7, 30), LocalTime.of(9, 30)));
        var first = learner("K21", null);
        var second = learner("K21", null);
        var outcomes = race(() -> registrations.register(first.account(), section),
                () -> registrations.register(second.account(), section));
        assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(java.util.Objects::nonNull).singleElement()
                .isInstanceOf(com.campus.academic.application.EnrollmentService.CapacityExceededException.class);
        assertThat(occupied(section)).isEqualTo(1);
    }

    @Test void exposesWindowFieldsOnTheAdministrativeTermContract() throws Exception {
        var opens = Instant.parse("2026-12-01T01:00:00Z");
        var closes = Instant.parse("2026-12-20T10:00:00Z");
        var created = mvc.perform(post("/api/v1/admin/academic/terms").header("Authorization", adminToken)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "code", prefix + "W1", "name", "Window term", "startDate", "2027-01-05", "endDate", "2027-05-30",
                                "registrationOpensAt", opens.toString(), "registrationClosesAt", closes.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationOpensAt").value("2026-12-01T01:00:00Z"))
                .andExpect(jsonPath("$.registrationClosesAt").value("2026-12-20T10:00:00Z")).andReturn();
        var id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put("/api/v1/admin/academic/terms/" + id).header("Authorization", adminToken)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "code", prefix + "W1", "name", "Window term", "startDate", "2027-01-05", "endDate", "2027-05-30",
                                "status", "PLANNED", "registrationOpensAt", opens.toString(), "registrationClosesAt", closes.toString(),
                                "expectedVersion", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrationOpensAt").value("2026-12-01T01:00:00Z"));
    }

    private record Learner(UUID account, UUID student, String token) {
        String authorization() { return "Bearer " + token; }
    }

    private Learner learner(String cohort, UUID programId) {
        var owner = account(RoleCode.USER);
        var student = students.create(prefix + "S" + UUID.randomUUID().toString().substring(0, 6), "Registration Student",
                null, owner.id(), unit, StudentStatus.ACTIVE).id();
        if (cohort != null || programId != null) profiles.updateAcademicProfile(admin, student, cohort, programId, 0);
        return new Learner(owner.id(), student, tokens.accessToken(owner));
    }

    private AcademicTerm term(Instant opensAt, Instant closesAt) {
        var start = LocalDate.now().plusDays(1);
        var value = delivery.createTerm(prefix + "T" + UUID.randomUUID().toString().substring(0, 6), "Registration term",
                start, start.plusMonths(4), opensAt, closesAt);
        return delivery.updateTerm(value.id(), value.code(), value.name(), value.startDate(), value.endDate(),
                AcademicTermStatus.ACTIVE, opensAt, closesAt, 0);
    }

    private UUID offering(AcademicTerm term, String code) {
        var course = catalog.createCourse(prefix + code, "Course " + code, 3, unit, AcademicCatalogStatus.ACTIVE);
        var value = delivery.createOffering(term.id(), course.id());
        return delivery.updateOffering(value.id(), AcademicDeliveryStatus.OPEN, 0).id();
    }

    private UUID section(AcademicTerm term, String code, int capacity, SectionSchedule schedule) {
        return openSection(offering(term, code), code, capacity, schedule);
    }

    private UUID openSection(UUID offering, String code, int capacity, SectionSchedule schedule) {
        var value = delivery.createSection(offering, prefix + code + UUID.randomUUID().toString().substring(0, 4),
                capacity, faculty, schedule);
        return delivery.updateSection(value.id(), value.code(), capacity, faculty, schedule, AcademicDeliveryStatus.OPEN, 0).id();
    }

    private static SectionSchedule schedule(int weekday, LocalTime start, LocalTime end) {
        return SectionSchedule.of(weekday, start, end, "A101");
    }

    private long occupied(UUID section) {
        return jdbc.queryForObject("select count(*) from academic_enrollments where section_id = ? and status = 'ENROLLED'",
                Long.class, section);
    }

    private List<Throwable> race(Runnable left, Runnable right) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(pool.submit(() -> failure(left)), pool.submit(() -> failure(right)));
            var outcomes = new ArrayList<Throwable>();
            for (var future : futures) outcomes.add(future.get(20, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Throwable failure(Runnable action) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> action.run());
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private UserAccount account(RoleCode role) {
        return accounts.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Registration user",
                passwords.encode("test-password"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }
}
