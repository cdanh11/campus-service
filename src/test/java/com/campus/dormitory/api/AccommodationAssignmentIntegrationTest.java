package com.campus.dormitory.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.campus.dormitory.application.*;
import com.campus.dormitory.domain.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class AccommodationAssignmentIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccommodationAssignmentService service;
    @Autowired DormitoryInventoryService inventoryService;
    @Autowired InventoryRepository inventory;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService units;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired PlatformTransactionManager transactions;
    UUID actor, unit, student, building, room, bed;
    String admin, user, prefix;
    static final String ROOT = "/api/v1/admin/dormitory/assignments";

    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8);
        var account = account(RoleCode.ADMIN); actor = account.id(); admin = tokens.accessToken(account);
        user = tokens.accessToken(account(RoleCode.USER));
        unit = units.create(prefix, "Unit", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE).id();
        student = student("S");
        building = inventoryService.create(actor, InventoryKind.BUILDING, null, prefix, "Building").id();
        room = inventoryService.create(actor, InventoryKind.ROOM, building, prefix, "Room").id();
        bed = inventoryService.create(actor, InventoryKind.BED, room, prefix, "Bed").id();
    }

    @Test void protectsEveryOperationAndMalformedQueryDoesNotWrite() throws Exception {
        long before = events();
        for (String bearer : List.of("", user)) for (var request : List.of(get(ROOT), get(ROOT + "/" + UUID.randomUUID()), post(ROOT), put(ROOT + "/" + UUID.randomUUID()))) {
            if (!bearer.isEmpty()) request.header("Authorization", "Bearer " + bearer);
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().is(bearer.isEmpty() ? 401 : 403));
        }
        call(post(ROOT), Map.of()).andExpect(status().isBadRequest());
        call(get(ROOT + "/bad-id"), null).andExpect(status().isBadRequest());
        call(get(ROOT + "/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
        for (var query : List.of(Map.entry("page", "-1"), Map.entry("page", "2147483647"), Map.entry("size", "0"), Map.entry("size", "101"),
                Map.entry("status", "UNKNOWN"), Map.entry("sort", "bedId,asc"), Map.entry("sort", "assignedAt,wrong"), Map.entry("studentId", "bad-id"))) {
            call(get(ROOT).param(query.getKey(), query.getValue()), null).andExpect(status().isBadRequest());
        }
        assertThat(events()).isEqualTo(before); assertThat(current()).isZero();
    }

    @Test void assignsReleasesRetainsHistoryAndNewAdmissionHasNewId() throws Exception {
        var body = new LinkedHashMap<String, Object>(Map.of("studentId", student, "bedId", bed));
        body.put("actorUserId", UUID.randomUUID());
        var assigned = read(call(post(ROOT), body).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        UUID id = UUID.fromString(assigned.get("id").asText());
        assertThat(read(call(get(ROOT + "/" + id), null).andExpect(status().isOk()))).isEqualTo(assigned);
        call(post(ROOT), body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOMMODATION_ALREADY_ASSIGNED"));
        long before = events();
        call(get(ROOT + "/" + id), null).andExpect(status().isOk());
        var release = new LinkedHashMap<String, Object>(Map.of("status", "RELEASED", "expectedVersion", 0));
        release.put("bedId", UUID.randomUUID()); release.put("studentId", UUID.randomUUID());
        var released = read(call(put(ROOT + "/" + id), release).andExpect(status().isOk()));
        assertThat(released.get("id")).isEqualTo(assigned.get("id")); assertThat(released.get("studentId")).isEqualTo(assigned.get("studentId"));
        assertThat(released.get("bedId")).isEqualTo(assigned.get("bedId")); assertThat(released.get("assignedAt")).isEqualTo(assigned.get("assignedAt"));
        assertThat(released.get("createdAt")).isEqualTo(assigned.get("createdAt"));
        assertThat(read(call(get(ROOT + "/" + id), null).andExpect(status().isOk()))).isEqualTo(released);
        assertThat(released.get("rowVersion").asLong()).isEqualTo(1); assertThat(current()).isZero();
        call(put(ROOT + "/" + id), release).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ROOT + "/" + id), Map.of("status", "RELEASED", "expectedVersion", 1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_ASSIGNMENT_STATE"));
        call(put(ROOT + "/" + id), Map.of("status", "ASSIGNED", "expectedVersion", 1)).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(before + 1);
        var next = service.create(actor, student, bed);
        assertThat(next.id()).isNotEqualTo(id); assertThat(service.get(id).status()).isEqualTo(AssignmentStatus.RELEASED);
        assertThat(current()).isEqualTo(1);
        for (long version : List.of(0L, 1L)) {
            var event = jdbc.queryForMap("SELECT * FROM dormitory_audit_events WHERE target_id = ? AND resource_version = ?", id, version);
            assertThat(event).containsEntry("actor_user_id", actor).containsEntry("resource_type", "ASSIGNMENT")
                    .containsEntry("action", version == 0 ? "ASSIGNED" : "RELEASED");
            assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status", version == 0 ? "ASSIGNED" : "RELEASED"));
        }
    }

    @Test void checksStudentBedEligibilityAndOccupiedBedDeactivation() {
        assertThatThrownBy(() -> service.create(actor, UUID.randomUUID(), bed)).isInstanceOf(AccommodationAssignmentService.StudentUnavailableException.class);
        var profile = students.get(student);
        students.update(student, profile.studentNumber(), profile.fullName(), profile.email(), profile.identityUserId(), unit, StudentStatus.INACTIVE, profile.rowVersion());
        assertThatThrownBy(() -> service.create(actor, student, bed)).isInstanceOf(AccommodationAssignmentService.StudentUnavailableException.class);
        profile = students.get(student);
        students.update(student, profile.studentNumber(), profile.fullName(), profile.email(), profile.identityUserId(), unit, StudentStatus.ACTIVE, profile.rowVersion());
        var value = service.create(actor, student, bed);
        assertThatThrownBy(() -> inventoryService.update(actor, InventoryKind.BED, bed, prefix, "Bed", InventoryStatus.INACTIVE, 0)).isInstanceOf(DormitoryInventoryService.InvalidStateException.class);
        assertThat(inventoryService.get(InventoryKind.BED, bed).rowVersion()).isZero();
        profile = students.get(student);
        students.update(student, profile.studentNumber(), profile.fullName(), profile.email(), profile.identityUserId(), unit, StudentStatus.INACTIVE, profile.rowVersion());
        assertThat(service.release(actor, value.id(), 0).status()).isEqualTo(AssignmentStatus.RELEASED);
        inventoryService.update(actor, InventoryKind.BED, bed, prefix, "Bed", InventoryStatus.INACTIVE, 0);
        assertThatThrownBy(() -> service.create(actor, student("ACTIVE"), bed)).isInstanceOf(DormitoryInventoryService.ReferenceUnavailableException.class);
    }

    @Test void auditFailureRollsBackAdmissionAndReleaseCompletely() throws Exception {
        long before = events(); var rows = rows();
        rejectAudit();
        try {
            call(post(ROOT), Map.of("studentId", student, "bedId", bed)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(rows()).isEqualTo(rows); assertThat(events()).isEqualTo(before);
        } finally { allowAudit(); }
        var assignment = service.create(actor, student, bed); before = events(); rows = rows();
        rejectAudit();
        try {
            call(put(ROOT + "/" + assignment.id()), Map.of("status", "RELEASED", "expectedVersion", 0)).andExpect(status().isInternalServerError());
            assertThat(rows()).isEqualTo(rows); assertThat(events()).isEqualTo(before); assertThat(current()).isEqualTo(1);
        } finally { allowAudit(); }
        service.release(actor, assignment.id(), 0); assertThat(current()).isZero();
    }

    @Test void twoStudentsCompetingForOneBedHaveOneCommittedEvent() throws Exception {
        UUID second = student("SECOND"); long before = events();
        var results = race(() -> service.create(actor, student, bed), () -> service.create(actor, second, bed));
        oneSuccess(results, AccommodationAssignmentService.AlreadyAssignedException.class);
        assertThat(current()).isEqualTo(1); assertThat(events()).isEqualTo(before + 1);
    }

    @Test void sameStudentInDifferentBuildingsCannotReceiveTwoPlaces() throws Exception {
        UUID otherBuilding = inventoryService.create(actor, InventoryKind.BUILDING, null, prefix + "B", "Other").id();
        UUID otherRoom = inventoryService.create(actor, InventoryKind.ROOM, otherBuilding, "RO", "Other").id();
        UUID otherBed = inventoryService.create(actor, InventoryKind.BED, otherRoom, "BE", "Other").id();
        long before = events();
        oneSuccess(race(() -> service.create(actor, student, bed), () -> service.create(actor, student, otherBed)), AccommodationAssignmentService.AlreadyAssignedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_assignments WHERE student_id = ? AND status = 'ASSIGNED'", Long.class, student)).isEqualTo(1);
        assertThat(events()).isEqualTo(before + 1);
    }

    @Test void competingReleasesHaveOneVersionAndOneEvent() throws Exception {
        var assignment = service.create(actor, student, bed); long before = events();
        oneSuccess(race(() -> service.release(actor, assignment.id(), 0), () -> service.release(actor, assignment.id(), 0)), DormitoryInventoryService.StaleVersionException.class);
        assertThat(service.get(assignment.id()).rowVersion()).isEqualTo(1); assertThat(current()).isZero(); assertThat(events()).isEqualTo(before + 1);
    }

    @Test void bedClosureAndAdmissionHaveConsistentPersistedOutcome() throws Exception {
        long before = events();
        oneSuccess(race(() -> inventoryService.update(actor, InventoryKind.BED, bed, prefix, "Bed", InventoryStatus.INACTIVE, 0),
                () -> service.create(actor, student, bed)), DormitoryInventoryService.InvalidStateException.class, DormitoryInventoryService.ReferenceUnavailableException.class);
        boolean active = inventoryService.get(InventoryKind.BED, bed).status() == InventoryStatus.ACTIVE;
        assertThat(current()).isEqualTo(active ? 1 : 0); assertThat(events()).isEqualTo(before + 1);
    }

    @Test void releaseAndNewAdmissionDoNotLoseHistoryOrOverbook() throws Exception {
        var assignment = service.create(actor, student, bed); UUID second = student("SECOND"); long before = events();
        var outcomes = race(() -> service.release(actor, assignment.id(), 0), () -> service.create(actor, second, bed));
        assertThat(outcomes.get(0)).isNull();
        if (outcomes.get(1) != null) assertThat(outcomes.get(1)).isInstanceOf(AccommodationAssignmentService.AlreadyAssignedException.class);
        assertThat(service.get(assignment.id()).status()).isEqualTo(AssignmentStatus.RELEASED);
        assertThat(current()).isEqualTo(outcomes.get(1) == null ? 1 : 0);
        assertThat(events()).isEqualTo(before + (outcomes.get(1) == null ? 2 : 1));
    }

    @Test void parentLockTimeoutPreventsAdmissionThenAcquisitionSucceeds() throws Exception {
        var pool = Executors.newSingleThreadExecutor(); long before = events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(holder -> {
                inventory.lock(InventoryKind.BED, bed);
                var future = pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(contender -> {
                    jdbc.execute("SET LOCAL lock_timeout = '500ms'"); service.create(actor, student, bed);
                })));
                try {
                    Throwable failure = future.get(10, TimeUnit.SECONDS); assertThat(failure).isNotNull();
                    while (failure.getCause() != null) failure = failure.getCause();
                    assertThat(failure).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo("55P03");
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            assertThat(current()).isZero(); assertThat(events()).isEqualTo(before);
            assertThat(service.create(actor, student, bed).status()).isEqualTo(AssignmentStatus.ASSIGNED);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void listsFiltersStableTiesAndUniqueOpenApiSchemas() throws Exception {
        var old = service.create(actor, student, bed); service.release(actor, old.id(), 0); var current = service.create(actor, student, bed);
        jdbc.update("UPDATE dormitory_assignments SET assigned_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE id IN (?, ?)", old.id(), current.id());
        var ordered = jdbc.queryForList("SELECT id FROM dormitory_assignments WHERE student_id = ? ORDER BY id", UUID.class, student);
        for (int page = 0; page < 2; page++) {
            var value = read(call(get(ROOT).param("studentId", student.toString()).param("size", "1").param("page", Integer.toString(page)).param("sort", "assignedAt,desc"), null).andExpect(status().isOk()));
            assertThat(value.get("content").get(0).get("id").asText()).isEqualTo(ordered.get(page).toString());
        }
        for (String field : List.of("status", "assignedAt", "createdAt", "updatedAt")) for (String order : List.of("asc", "desc")) {
            call(get(ROOT).param("studentId", student.toString()).param("bedId", bed.toString()).param("status", "RELEASED").param("sort", field + "," + order), null)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        var spec = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for (var operation : List.of(spec.path("paths").path(ROOT).path("post"), spec.path("paths").path(ROOT).path("get"),
                spec.path("paths").path(ROOT + "/{id}").path("get"), spec.path("paths").path(ROOT + "/{id}").path("put"))) {
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
        }
        var update = spec.path("components").path("schemas").path("AccommodationAssignmentReleaseRequest");
        assertThat(update.path("properties").has("expectedVersion")).isTrue(); assertThat(update.path("properties").has("bedId")).isFalse();
    }

    private List<Throwable> race(Supplier<?> left, Supplier<?> right) throws Exception {
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Throwable>>();
            for (var task : List.of(left, right)) futures.add(pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return catchThrowable(task::get);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    private void oneSuccess(List<Throwable> results, Class<?>... failures) {
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results).filteredOn(Objects::nonNull).singleElement().satisfies(failure -> assertThat(failure).isInstanceOfAny(failures));
    }
    private UUID student(String suffix) { return students.create(prefix + suffix, "Student", null, null, unit, StudentStatus.ACTIVE).id(); }
    private UserAccount account(RoleCode role) { return users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Admin",
            passwords.encode("valid-password"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now())); }
    private long events() { return jdbc.queryForObject("SELECT count(*) FROM dormitory_audit_events WHERE actor_user_id = ?", Long.class, actor); }
    private long current() { return jdbc.queryForObject("SELECT count(*) FROM dormitory_assignments WHERE bed_id = ? AND status = 'ASSIGNED'", Long.class, bed); }
    private List<Map<String, Object>> rows() { return jdbc.queryForList("SELECT * FROM dormitory_assignments ORDER BY id"); }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + admin).contentType("application/json");
        if (body != null) request.content(json.writeValueAsString(body)); return mvc.perform(request);
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_assignment_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_assignment_audit_test BEFORE INSERT ON dormitory_audit_events FOR EACH ROW EXECUTE FUNCTION reject_assignment_audit_test()");
    }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_assignment_audit_test ON dormitory_audit_events"); }
}
