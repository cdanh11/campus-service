package com.campus.academic.api;

import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.academic.application.*;
import com.campus.academic.domain.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.*;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class EnrollmentIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired EnrollmentService enrollments;
    @Autowired AcademicDeliveryService delivery;
    @Autowired AcademicCatalogService catalog;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired StudentManagementService students;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ClassSectionRepository sections;
    UUID section, student, unit;
    String prefix;
    static final String ROOT = "/api/v1/admin/academic/enrollments";

    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8);
        unit = units.create(prefix, "Enrollment Unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        var course = catalog.createCourse(prefix, "Enrollment Course", 3, unit, AcademicCatalogStatus.ACTIVE);
        var faculty = personnel.create(prefix, "Teacher", null, null, PersonnelType.FACULTY, unit, PersonnelStatus.ACTIVE);
        var term = delivery.createTerm(prefix, "Term", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        term = delivery.updateTerm(term.id(), term.code(), term.name(), term.startDate(), term.endDate(), AcademicTermStatus.ACTIVE, 0);
        var offering = delivery.createOffering(term.id(), course.id());
        offering = delivery.updateOffering(offering.id(), AcademicDeliveryStatus.OPEN, 0);
        var value = delivery.createSection(offering.id(), prefix, 1, faculty.id());
        section = delivery.updateSection(value.id(), value.code(), value.capacity(), faculty.id(), AcademicDeliveryStatus.OPEN, 0).id();
        student = student(StudentStatus.ACTIVE);
    }

    private UUID student(StudentStatus status) {
        return students.create(UUID.randomUUID().toString().substring(0, 20), "Student", null, null, unit, status).id();
    }

    @Test void protectsAllRoutesForAnonymousAndNonAdmin() throws Exception {
        String user = token(RoleCode.USER);
        for (String caller : List.of("", user)) {
            for (var request : List.of(get(ROOT), get(ROOT + "/" + UUID.randomUUID()), post(ROOT), put(ROOT + "/" + UUID.randomUUID()))) {
                if (!caller.isEmpty()) request.header("Authorization", "Bearer " + caller);
                mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().is(caller.isEmpty() ? 401 : 403));
            }
        }
    }

    @Test void withdrawsReenrollsAndRejectsStaleWritesWithoutLosingIdentity() {
        var original = enrollments.create(student, section);
        var persistedCreation = enrollments.get(original.id()).createdAt();
        assertThatThrownBy(() -> enrollments.create(student, section)).isInstanceOf(EnrollmentService.DuplicateException.class);
        var withdrawn = enrollments.update(original.id(), EnrollmentStatus.WITHDRAWN, 0);
        assertThat(withdrawn.rowVersion()).isEqualTo(1);
        assertThatThrownBy(() -> enrollments.update(original.id(), EnrollmentStatus.ENROLLED, 0)).isInstanceOf(EnrollmentService.StaleVersionException.class);
        var restored = enrollments.update(original.id(), EnrollmentStatus.ENROLLED, 1);
        assertThat(restored.rowVersion()).isEqualTo(2);
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.createdAt()).isEqualTo(persistedCreation);
        assertThat(occupied()).isEqualTo(1);
    }

    @Test void rejectsUnavailableStudentsAndFullSectionWithoutPartialWrites() {
        assertThatThrownBy(() -> enrollments.create(UUID.randomUUID(), section)).isInstanceOf(EnrollmentService.StudentUnavailableException.class);
        assertThatThrownBy(() -> enrollments.create(student(StudentStatus.INACTIVE), section)).isInstanceOf(EnrollmentService.StudentUnavailableException.class);
        enrollments.create(student, section);
        assertThatThrownBy(() -> enrollments.create(student(StudentStatus.ACTIVE), section)).isInstanceOf(EnrollmentService.CapacityExceededException.class);
        assertThat(occupied()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from academic_enrollments where section_id = ?", Long.class, section)).isEqualTo(1);
    }

    @Test void permitsHistoricalWithdrawalButRejectsReenrollmentAfterClosure() {
        var value = enrollments.create(student, section);
        var current = delivery.section(section);
        delivery.updateSection(section, current.code(), current.capacity(), current.facultyId(), AcademicDeliveryStatus.CLOSED, current.rowVersion());
        enrollments.update(value.id(), EnrollmentStatus.WITHDRAWN, 0);
        assertThatThrownBy(() -> enrollments.update(value.id(), EnrollmentStatus.ENROLLED, 1)).isInstanceOf(EnrollmentService.InvalidStateException.class);
        assertThat(enrollments.get(value.id()).rowVersion()).isEqualTo(1);
        assertThat(occupied()).isZero();
    }

    @Test void competingStudentsCannotBothTakeTheLastSeat() throws Exception {
        var second = student(StudentStatus.ACTIVE);
        var outcomes = race(() -> enrollments.create(student, section), () -> enrollments.create(second, section));
        assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(Objects::nonNull).singleElement().isInstanceOf(EnrollmentService.CapacityExceededException.class);
        assertThat(occupied()).isEqualTo(1);
    }

    @Test void duplicateRaceCreatesOnlyOneMembership() throws Exception {
        var outcomes = race(() -> enrollments.create(student, section), () -> enrollments.create(student, section));
        assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(Objects::nonNull).singleElement().isInstanceOf(EnrollmentService.DuplicateException.class);
        assertThat(occupied()).isEqualTo(1);
    }

    @Test void competingWithdrawalsRejectTheStaleTransaction() throws Exception {
        var value = enrollments.create(student, section);
        var outcomes = race(() -> enrollments.update(value.id(), EnrollmentStatus.WITHDRAWN, 0),
                () -> enrollments.update(value.id(), EnrollmentStatus.WITHDRAWN, 0));
        assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(Objects::nonNull).singleElement().isInstanceOf(EnrollmentService.StaleVersionException.class);
        assertThat(enrollments.get(value.id()).rowVersion()).isEqualTo(1);
        assertThat(occupied()).isZero();
    }

    @Test void reenrollmentAndNewStudentCompeteForTheSameReleasedSeat() throws Exception {
        var value = enrollments.create(student, section);
        enrollments.update(value.id(), EnrollmentStatus.WITHDRAWN, 0);
        var second = student(StudentStatus.ACTIVE);
        var outcomes = race(() -> enrollments.update(value.id(), EnrollmentStatus.ENROLLED, 1), () -> enrollments.create(second, section));
        assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(Objects::nonNull).singleElement().isInstanceOf(EnrollmentService.CapacityExceededException.class);
        assertThat(occupied()).isEqualTo(1);
        assertThat(enrollments.get(value.id()).rowVersion()).isIn(1L, 2L);
    }

    @Test void closureAndEnrollmentHaveAConsistentSerializedOutcome() throws Exception {
        var current = delivery.section(section);
        var outcomes = race(() -> enrollments.create(student, section), () -> delivery.updateSection(section, current.code(),
                current.capacity(), current.facultyId(), AcademicDeliveryStatus.CLOSED, current.rowVersion()));
        assertThat(outcomes.get(1)).isNull();
        if (outcomes.get(0) == null) assertThat(occupied()).isEqualTo(1);
        else { assertThat(outcomes.get(0)).isInstanceOf(EnrollmentService.InvalidStateException.class); assertThat(occupied()).isZero(); }
        assertThat(delivery.section(section).status()).isEqualTo(AcademicDeliveryStatus.CLOSED);
        assertThatThrownBy(() -> enrollments.create(student(StudentStatus.ACTIVE), section)).isInstanceOf(EnrollmentService.InvalidStateException.class);
    }

    @Test void productionSectionLockBlocksEnrollmentUntilReleased() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                sections.lock(section);
                var future = pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(other -> {
                    jdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    enrollments.create(student, section);
                })));
                try {
                    Throwable failure = future.get(10, TimeUnit.SECONDS);
                    assertThat(failure).isNotNull();
                    while (failure.getCause() != null) failure = failure.getCause();
                    assertThat(failure).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) failure).getSQLState()).isEqualTo("55P03");
                } catch (Exception exception) { throw new IllegalStateException(exception); }
            });
            // TransactionTemplate reliably commits/rolls back the holder before the next acquisition.
            assertThat(occupied()).isZero();
            assertThat(enrollments.create(student, section).status()).isEqualTo(EnrollmentStatus.ENROLLED);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void refreshesCachedSectionAfterACommittedClosureBeforeAdmission() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                var cached = delivery.section(section);
                try {
                    pool.submit(() -> delivery.updateSection(section, cached.code(), cached.capacity(), cached.facultyId(),
                            AcademicDeliveryStatus.CLOSED, cached.rowVersion())).get(10, TimeUnit.SECONDS);
                } catch (Exception exception) { throw new IllegalStateException(exception); }
                assertThatThrownBy(() -> enrollments.create(student, section)).isInstanceOf(EnrollmentService.InvalidStateException.class);
                // The expected rejection marks the joined transaction rollback-only.
                transaction.setRollbackOnly();
            });
            assertThat(occupied()).isZero();
            assertThat(delivery.section(section).status()).isEqualTo(AcademicDeliveryStatus.CLOSED);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private long occupied() { return jdbc.queryForObject("select count(*) from academic_enrollments where section_id = ? and status = 'ENROLLED'", Long.class, section); }

    @Test void exposesAdminLifecycleAndConsistentErrorContract() throws Exception {
        var admin = token(RoleCode.ADMIN);
        var response = mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("studentId", student, "sectionId", section))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        var id = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get(ROOT + "/" + id).header("Authorization", "Bearer " + admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
        mvc.perform(put(ROOT + "/" + id).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content("{\"status\":\"WITHDRAWN\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        mvc.perform(put(ROOT + "/" + id).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content("{\"status\":\"ENROLLED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mvc.perform(get(ROOT + "/" + UUID.randomUUID()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.path").exists());
    }

    @Test void validatesHttpRequestsAndBoundedDatabaseQueries() throws Exception {
        String admin = token(RoleCode.ADMIN);
        for (String body : List.of("{}", "{\"studentId\":\"bad\"}", "{")) {
            mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        var value = enrollments.create(student, section);
        for (String body : List.of("{}", "{\"status\":\"WITHDRAWN\",\"expectedVersion\":-1}", "{\"status\":\"INVALID\",\"expectedVersion\":0}")) {
            mvc.perform(put(ROOT + "/" + value.id()).header("Authorization", "Bearer " + admin).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String query : List.of("page=-1", "size=0", "size=101", "page=2147483647&size=2", "sort=studentId,asc", "sort=status,bad", "status=INVALID", "sectionId=bad")) {
            mvc.perform(get(ROOT + "?" + query).header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(ROOT).param("studentId", student.toString()).param("sectionId", section.toString())
                .param("status", "ENROLLED").param("size", "1").param("sort", "status,asc").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(value.id().toString()));
        mvc.perform(get(ROOT).param("sectionId", UUID.randomUUID().toString()).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void returnsSpecificHttpConflictsWithoutChangingMembership() throws Exception {
        String admin = token(RoleCode.ADMIN);
        for (UUID unavailable : List.of(UUID.randomUUID(), student(StudentStatus.INACTIVE))) {
            mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("studentId", unavailable, "sectionId", section))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_REFERENCE_UNAVAILABLE"));
        }
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("studentId", student, "sectionId", UUID.randomUUID()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_NOT_FOUND"));
        var value = enrollments.create(student, section);
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("studentId", student, "sectionId", section))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_ALREADY_EXISTS"));
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("studentId", student(StudentStatus.ACTIVE), "sectionId", section))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SECTION_CAPACITY_EXCEEDED"));
        mvc.perform(put(ROOT + "/" + value.id()).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content("{\"status\":\"ENROLLED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_STATE"));
        assertThat(enrollments.get(value.id()).rowVersion()).isZero(); assertThat(occupied()).isEqualTo(1);
    }

    @Test void withdrawalAfterStudentDeactivationIsAllowedButReenrollmentIsNot() {
        var value = enrollments.create(student, section);
        var old = students.get(student);
        students.update(student, old.studentNumber(), old.fullName(), old.email(), old.identityUserId(), old.organizationUnitId(), StudentStatus.INACTIVE, old.rowVersion());
        enrollments.update(value.id(), EnrollmentStatus.WITHDRAWN, 0);
        assertThatThrownBy(() -> enrollments.update(value.id(), EnrollmentStatus.ENROLLED, 1)).isInstanceOf(EnrollmentService.StudentUnavailableException.class);
        assertThat(enrollments.get(value.id()).status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(enrollments.get(value.id()).rowVersion()).isEqualTo(1);
        assertThat(occupied()).isZero();
    }

    @Test void closedAndDraftSectionsRejectNewEnrollment() {
        var old = delivery.section(section);
        delivery.updateSection(section, old.code(), old.capacity(), old.facultyId(), AcademicDeliveryStatus.CLOSED, old.rowVersion());
        assertThatThrownBy(() -> enrollments.create(student, section)).isInstanceOf(EnrollmentService.InvalidStateException.class);
        var draft = delivery.createSection(old.offeringId(), "DRAFT", 1, null);
        assertThatThrownBy(() -> enrollments.create(student, draft.id())).isInstanceOf(EnrollmentService.InvalidStateException.class);
        assertThat(occupied()).isZero();
    }

    @Test void stablePaginationUsesIdAsTieBreakerAndRespectsStatusFilter() throws Exception {
        String admin = token(RoleCode.ADMIN);
        var first = enrollments.create(student, section);
        enrollments.update(first.id(), EnrollmentStatus.WITHDRAWN, 0);
        var second = enrollments.create(student(StudentStatus.ACTIVE), section);
        var expected = List.of(first, second).stream().map(Enrollment::id).map(UUID::toString).sorted().toList();
        for (int page = 0; page < 2; page++) {
            mvc.perform(get(ROOT).param("sectionId", section.toString()).param("sort", "createdAt,asc")
                    .param("page", String.valueOf(page)).param("size", "1").header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
        // Force a genuine timestamp tie, then assert the documented ID ordering.
        jdbc.update("UPDATE academic_enrollments SET created_at = TIMESTAMPTZ '2026-01-01T00:00:00Z' WHERE section_id = ?", section);
        for (int page = 0; page < 2; page++) {
            mvc.perform(get(ROOT).param("sectionId", section.toString()).param("sort", "createdAt,asc")
                    .param("page", String.valueOf(page)).param("size", "1").header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(expected.get(page)));
        }
        mvc.perform(get(ROOT).param("sectionId", section.toString()).param("status", "WITHDRAWN").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(first.id().toString()));
    }

    private String token(RoleCode role) {
        var user = users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Enrollment Admin",
                passwords.encode("test-password"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
        return tokens.accessToken(user);
    }

    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Throwable>>();
            for (var task : List.of(first, second)) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                return catchThrowable(task::run);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
