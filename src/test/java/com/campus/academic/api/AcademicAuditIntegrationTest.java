package com.campus.academic.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.time.LocalDate;
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
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class AcademicAuditIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AcademicCatalogService catalog;
    @Autowired AcademicDeliveryService delivery;
    @Autowired EnrollmentService enrollments;
    @Autowired AcademicAdministrationService administration;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired StudentManagementService students;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    UUID actor, unit, term, course, offering, section, faculty, student;
    String admin, prefix;
    static final String ROOT = "/api/v1/admin/academic/";

    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8);
        var user = users.save(UserAccount.create(UUID.randomUUID(), prefix + "@campus.example", "Audit Admin", passwords.encode("test-password"),
                AccountStatus.ACTIVE, Set.of(roles.findByCode(RoleCode.ADMIN).orElseThrow()), Instant.now()));
        actor = user.id(); admin = tokens.accessToken(user);
        unit = units.create(prefix, "Unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        course = catalog.createCourse(prefix, "Course", 3, unit, AcademicCatalogStatus.ACTIVE).id();
        faculty = personnel.create(prefix, "Faculty", null, null, PersonnelType.FACULTY, unit, PersonnelStatus.ACTIVE).id();
        student = students.create(prefix, "Student", null, null, unit, StudentStatus.ACTIVE).id();
        var value = delivery.createTerm(prefix, "Term", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        term = delivery.updateTerm(value.id(), value.code(), value.name(), value.startDate(), value.endDate(), AcademicTermStatus.ACTIVE, 0).id();
        var open = delivery.createOffering(term, course);
        offering = delivery.updateOffering(open.id(), AcademicDeliveryStatus.OPEN, 0).id();
        var draft = delivery.createSection(offering, prefix, 1, faculty);
        section = delivery.updateSection(draft.id(), draft.code(), 1, faculty, AcademicDeliveryStatus.OPEN, 0).id();
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses", "terms", "offerings", "sections", "enrollments"})
    void recordsEveryHttpMutationWithTrustedActorAndResultVersion(String route) throws Exception {
        var created = create(route);
        UUID id = UUID.fromString(created.get("id").asText());
        var event = jdbc.queryForMap("SELECT * FROM academic_audit_events WHERE target_id = ?", id);
        assertThat(event).containsEntry("actor_user_id", actor).containsEntry("resource_type", resource(route))
                .containsEntry("action", "CREATED").containsEntry("resource_version", created.get("rowVersion").asLong());
        assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status", created.get("status").asText()));
        assertThat(event.get("occurred_at")).isNotNull();
        long before = events();
        call(get(ROOT + route + "/" + id), null).andExpect(status().isOk());
        call(get(ROOT + route), null).andExpect(status().isOk());
        assertThat(events()).isEqualTo(before);
        var updated = json.readTree(call(put(ROOT + route + "/" + id), updateBody(route, created)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var last = jdbc.queryForMap("SELECT * FROM academic_audit_events WHERE target_id = ? AND action <> 'CREATED'", id);
        assertThat(last).containsEntry("actor_user_id", actor).containsEntry("resource_type", resource(route))
                .containsEntry("target_id", id).containsEntry("resource_version", updated.get("rowVersion").asLong())
                .containsEntry("action", route.equals("enrollments") ? "WITHDRAWN" : "UPDATED");
        assertThat(json.readTree(last.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status", updated.get("status").asText()));
        assertThat(last.get("occurred_at")).isNotNull();
        assertThat(events()).isEqualTo(before + 1);
        call(put(ROOT + route + "/" + id), updateBody(route, created)).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(before + 1);
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses", "terms", "offerings", "sections", "enrollments"})
    void databaseAuditFailureRollsBackCreatesAndUpdates(String route) throws Exception {
        String table = table(route);
        var body = createBody(route);
        var before = jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
        long count = events();
        rejectAudit();
        try {
            call(post(ROOT + route), body).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")).isEqualTo(before);
            assertThat(events()).isEqualTo(count);
        } finally { allowAudit(); }
        // Reuse the same identifiers/code to prove the failed create left no uniqueness reservation.
        var created = json.readTree(call(post(ROOT + route), body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asText());
        var old = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
        count = events();
        rejectAudit();
        try {
            call(put(ROOT + route + "/" + id), updateBody(route, created)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id)).isEqualTo(old);
            assertThat(events()).isEqualTo(count);
        } finally { allowAudit(); }
    }

    @Test void reenrollmentAuditFailureDoesNotTakeTheReleasedSeat() throws Exception {
        var created = create("enrollments");
        var id = UUID.fromString(created.get("id").asText());
        call(put(ROOT + "enrollments/" + id), Map.of("status", "WITHDRAWN", "expectedVersion", 0)).andExpect(status().isOk());
        long before = events();
        rejectAudit();
        try {
            call(put(ROOT + "enrollments/" + id), Map.of("status", "ENROLLED", "expectedVersion", 1)).andExpect(status().isInternalServerError());
            assertThat(enrollments.get(id).status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
            assertThat(enrollments.get(id).rowVersion()).isEqualTo(1);
            assertThat(events()).isEqualTo(before);
        } finally { allowAudit(); }
        call(put(ROOT + "enrollments/" + id), Map.of("status", "ENROLLED", "expectedVersion", 1)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT action FROM academic_audit_events WHERE target_id = ? AND resource_version = 2", String.class, id)).isEqualTo("REENROLLED");
        assertThat(events()).isEqualTo(before + 1);
    }

    @Test void invalidAndUnauthorizedRequestsCannotWriteAuditOrSpoofActor() throws Exception {
        long before = events();
        mvc.perform(post(ROOT + "programs").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        call(post(ROOT + "programs"), Map.of()).andExpect(status().isBadRequest());
        var body = createBody("programs"); body.put("actorUserId", UUID.randomUUID());
        var created = json.readTree(call(post(ROOT + "programs"), body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(events()).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM academic_audit_events WHERE target_id = ?", UUID.class, UUID.fromString(created.get("id").asText()))).isEqualTo(actor);
        call(post(ROOT + "programs"), body).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(before + 1);
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses", "terms", "offerings", "sections", "enrollments"})
    void deniedAndMalformedMutationsPreserveBusinessRowsAndAudit(String route) throws Exception {
        var created = create(route);
        String path = ROOT + route;
        String target = path + "/" + created.get("id").asText();
        var before = jdbc.queryForList("SELECT * FROM " + table(route) + " ORDER BY id");
        long count = events();
        var ordinary = users.save(UserAccount.create(UUID.randomUUID(), prefix + "-user@campus.example", "Ordinary User",
                passwords.encode("test-password"), AccountStatus.ACTIVE,
                Set.of(roles.findByCode(RoleCode.USER).orElseThrow()), Instant.now()));
        String bearer = tokens.accessToken(ordinary);
        // Authorization must reject before body validation or any mutation runs.
        for (var request : List.of(post(path), put(target))) {
            mvc.perform(request.contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        for (var request : List.of(post(path), put(target))) {
            mvc.perform(request.header("Authorization", "Bearer " + bearer).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        call(post(path), Map.of()).andExpect(status().isBadRequest());
        call(put(target), Map.of()).andExpect(status().isBadRequest());
        call(put(path + "/not-a-uuid"), updateBody(route, created)).andExpect(status().isBadRequest());
        call(get(path).param("page", "-1"), null).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForList("SELECT * FROM " + table(route) + " ORDER BY id")).isEqualTo(before);
        assertThat(events()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_audit_events WHERE actor_user_id = ?", Long.class, ordinary.id())).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void competingCatalogUpdatesAuditOnlyTheCommittedVersion(String route) throws Exception {
        var created = create(route);
        UUID id = UUID.fromString(created.get("id").asText());
        long before = events();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                var body = updateBody(route, created);
                body.put(route.equals("programs") ? "name" : "title", "Competing " + index);
                String content = json.writeValueAsString(body);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                    return mvc.perform(put(ROOT + route + "/" + id).header("Authorization", "Bearer " + admin)
                            .contentType("application/json").content(content)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            var saved = json.readTree(call(get(ROOT + route + "/" + id), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(saved.get("rowVersion").asLong()).isEqualTo(1);
            assertThat(events()).isEqualTo(before + 1);
            var event = jdbc.queryForMap("SELECT * FROM academic_audit_events WHERE target_id = ? AND action = 'UPDATED'", id);
            assertThat(event).containsEntry("actor_user_id", actor).containsEntry("resource_type", resource(route))
                    .containsEntry("resource_version", 1L);
            assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status", saved.get("status").asText()));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void generatedOpenApiDeclaresBearerSecurityOnEveryAcademicOperation() throws Exception {
        var spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String route : List.of("programs", "courses", "terms", "offerings", "sections", "enrollments")) {
            for (String suffix : List.of("", "/{id}")) {
                var path = spec.path("paths").path(ROOT + route + suffix);
                assertThat(path.isMissingNode()).isFalse();
                for (String operation : suffix.isEmpty() ? List.of("get", "post") : List.of("get", "put")) {
                    assertThat(path.path(operation).path("security").get(0).has("bearerAuth")).isTrue();
                }
            }
        }
    }

    @Test void onlyTheWinningLastSeatTransactionCommitsAnAuditEvent() throws Exception {
        UUID second = students.create(prefix + "SECOND", "Second Student", null, null, unit, StudentStatus.ACTIVE).id();
        long before = events();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Throwable>>();
            for (UUID candidate : List.of(student, second)) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                return catchThrowable(() -> administration.create(actor, candidate, section));
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(Objects::isNull).hasSize(1);
            assertThat(results).filteredOn(Objects::nonNull).singleElement().isInstanceOf(EnrollmentService.CapacityExceededException.class);
            assertThat(events()).isEqualTo(before + 1);
            UUID enrolled = jdbc.queryForObject("SELECT id FROM academic_enrollments WHERE section_id = ? AND status = 'ENROLLED'", UUID.class, section);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_audit_events WHERE target_id = ? AND action = 'CREATED'", Long.class, enrolled)).isEqualTo(1);
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void postgresUsesExistingEnrollmentIndexesForSelectiveOccupancyAndStudentQueries() throws Exception {
        // Synthetic query evidence only: this is not an end-to-end latency/load benchmark.
        jdbc.update("""
                WITH new_students AS (
                    INSERT INTO students (id, student_number, full_name, organization_unit_id)
                    SELECT gen_random_uuid(), ? || 'Q' || n, 'Query Student', ? FROM generate_series(1, 100) n RETURNING id
                ), new_sections AS (
                    INSERT INTO academic_class_sections (id, offering_id, code, capacity, faculty_id, status)
                    SELECT gen_random_uuid(), ?, ? || 'Q' || n, 100, ?, 'OPEN' FROM generate_series(1, 100) n RETURNING id
                )
                INSERT INTO academic_enrollments (id, student_id, section_id)
                SELECT gen_random_uuid(), s.id, c.id FROM new_students s CROSS JOIN new_sections c
                """, prefix, unit, offering, prefix, faculty);
        jdbc.execute("ANALYZE academic_enrollments");
        UUID selectedSection = jdbc.queryForObject("SELECT id FROM academic_class_sections WHERE code = ?", UUID.class, prefix + "Q1");
        UUID selectedStudent = jdbc.queryForObject("SELECT id FROM students WHERE student_number = ?", UUID.class, prefix + "Q1");
        var occupancyPlan = json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT count(*) FROM academic_enrollments WHERE section_id = ? AND status = 'ENROLLED'", String.class, selectedSection));
        var studentPlan = json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM academic_enrollments WHERE student_id = ? AND status = 'ENROLLED'", String.class, selectedStudent));
        assertThat(occupancyPlan.findValuesAsText("Index Name")).contains("ix_academic_enrollment_section_status");
        assertThat(studentPlan.findValuesAsText("Index Name")).contains("ix_academic_enrollment_student_status");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_enrollments WHERE section_id = ? AND status = 'ENROLLED'", Long.class, selectedSection)).isEqualTo(100);
    }

    private JsonNode create(String route) throws Exception {
        return json.readTree(call(post(ROOT + route), createBody(route)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private Map<String, Object> createBody(String route) {
        var body = new LinkedHashMap<String, Object>();
        switch (route) {
            case "programs" -> body.putAll(Map.of("code", prefix + "P", "name", "Program", "organizationUnitId", unit));
            case "courses" -> body.putAll(Map.of("code", prefix + "C", "title", "Course", "credits", 3, "organizationUnitId", unit));
            case "terms" -> body.putAll(Map.of("code", prefix + "T", "name", "Term", "startDate", "2026-01-01", "endDate", "2026-06-01"));
            case "offerings" -> {
                UUID other = catalog.createCourse(prefix + "O", "Other Course", 3, unit, AcademicCatalogStatus.ACTIVE).id();
                body.putAll(Map.of("termId", term, "courseId", other));
            }
            case "sections" -> body.putAll(Map.of("offeringId", offering, "code", prefix + "S", "capacity", 1, "facultyId", faculty));
            case "enrollments" -> body.putAll(Map.of("studentId", student, "sectionId", section));
            default -> throw new IllegalArgumentException(route);
        }
        return body;
    }
    private Map<String, Object> updateBody(String route, JsonNode value) {
        var body = new LinkedHashMap<String, Object>();
        body.put("expectedVersion", value.get("rowVersion").asLong());
        switch (route) {
            case "programs" -> body.putAll(Map.of("code", value.get("code").asText(), "name", "Updated Program", "organizationUnitId", unit, "status", "ACTIVE"));
            case "courses" -> body.putAll(Map.of("code", value.get("code").asText(), "title", "Updated Course", "credits", 4, "organizationUnitId", unit, "status", "ACTIVE"));
            case "terms" -> body.putAll(Map.of("code", value.get("code").asText(), "name", "Updated Term", "startDate", "2026-01-01", "endDate", "2026-06-01", "status", "ACTIVE"));
            case "offerings" -> body.put("status", "OPEN");
            case "sections" -> body.putAll(Map.of("code", value.get("code").asText(), "capacity", 1, "facultyId", faculty, "status", "OPEN"));
            case "enrollments" -> body.put("status", "WITHDRAWN");
            default -> throw new IllegalArgumentException(route);
        }
        return body;
    }
    private String resource(String route) { return Map.of("programs", "PROGRAM", "courses", "COURSE", "terms", "TERM", "offerings", "COURSE_OFFERING", "sections", "CLASS_SECTION", "enrollments", "ENROLLMENT").get(route); }
    private String table(String route) { return Map.of("programs", "academic_programs", "courses", "academic_courses", "terms", "academic_terms", "offerings", "academic_course_offerings", "sections", "academic_class_sections", "enrollments", "academic_enrollments").get(route); }
    private long events() { return jdbc.queryForObject("SELECT count(*) FROM academic_audit_events WHERE actor_user_id = ?", Long.class, actor); }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + admin).contentType("application/json");
        if (body != null) request.content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private void rejectAudit() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_academic_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_academic_audit_test BEFORE INSERT ON academic_audit_events FOR EACH ROW EXECUTE FUNCTION reject_academic_audit_test()");
    }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_academic_audit_test ON academic_audit_events"); }
}
