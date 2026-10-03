package com.campus.academic.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.academic.application.AcademicCatalogService;
import com.campus.academic.domain.AcademicCatalogStatus;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.personnel.application.FacultyStaffManagementService;
import com.campus.personnel.domain.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class AcademicDeliveryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired OrganizationUnitManagementService units;
    @Autowired FacultyStaffManagementService personnel;
    @Autowired AcademicCatalogService catalog;
    String admin, prefix;
    UUID unit, course, faculty;
    static final String ROOT = "/api/v1/admin/academic/";

    @BeforeEach
    void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        admin = token(RoleCode.ADMIN);
        unit = units.create(prefix, "Academic Unit", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
        course = catalog.createCourse(prefix, "Course", 3, unit, AcademicCatalogStatus.ACTIVE).id();
        faculty = member(PersonnelType.FACULTY, PersonnelStatus.ACTIVE);
    }

    @ParameterizedTest @ValueSource(strings = {"terms", "offerings", "sections"})
    void protectsEveryDeliveryOperation(String resource) throws Exception {
        String user = token(RoleCode.USER);
        for (String token : List.of("", user)) {
            for (var request : List.of(get(ROOT + resource), get(ROOT + resource + "/" + UUID.randomUUID()),
                    post(ROOT + resource), put(ROOT + resource + "/" + UUID.randomUUID()))) {
                if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
                mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().is(token.isEmpty() ? 401 : 403));
            }
        }
    }

    @Test
    void followsTermOfferingSectionLifecycleAndFreezesOpenedProfiles() throws Exception {
        var term = create("terms", termBody());
        assertThat(term.get("status").asText()).isEqualTo("PLANNED");
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        assertThat(offering.get("organizationUnitId").asText()).isEqualTo(unit.toString());
        var section = create("sections", sectionBody(id(offering), null));
        assertThat(section.get("facultyId").isNull()).isTrue();
        error(update("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0)), 409, "INVALID_ACADEMIC_STATE");
        term = updateOk("terms", term, termUpdate("ACTIVE", 0));
        offering = updateOk("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0));
        var change = sectionUpdate(section, "OPEN", null, 0);
        error(update("sections", section, change), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        change = sectionUpdate(section, "OPEN", faculty, 0);
        section = updateOk("sections", section, change);
        assertThat(section.get("rowVersion").asInt()).isEqualTo(1);
        error(update("offerings", offering, Map.of("status", "CLOSED", "expectedVersion", 1)), 409, "INVALID_ACADEMIC_STATE");
        error(update("terms", term, termUpdate("CLOSED", 1)), 409, "INVALID_ACADEMIC_STATE");
        var resized = sectionUpdate(section, "OPEN", faculty, 1);
        resized.put("capacity", 40);
        error(update("sections", section, resized), 409, "INVALID_ACADEMIC_STATE");
        error(update("sections", section, sectionUpdate(section, "DRAFT", faculty, 1)), 409, "INVALID_ACADEMIC_STATE");
        section = updateOk("sections", section, sectionUpdate(section, "CLOSED", faculty, 1));
        updateOk("offerings", offering, Map.of("status", "CLOSED", "expectedVersion", 1));
        updateOk("terms", term, termUpdate("CLOSED", 1));
        error(update("sections", section, sectionUpdate(section, "OPEN", faculty, 2)), 409, "INVALID_ACADEMIC_STATE");
    }

    @Test
    void validatesDatesAndPreservesVersionWhenStaleOrDuplicate() throws Exception {
        var bad = termBody(); bad.put("endDate", "2026-01-01");
        error(call(post(ROOT + "terms"), bad), 400, "VALIDATION_FAILED");
        var first = create("terms", termBody());
        error(call(post(ROOT + "terms"), termBody()), 409, "ACADEMIC_RESOURCE_ALREADY_EXISTS");
        var baseline = read(call(get(ROOT + "terms/" + id(first)), null).andExpect(status().isOk()));
        var active = updateOk("terms", first, termUpdate("ACTIVE", 0));
        assertThat(active.get("createdAt")).isEqualTo(baseline.get("createdAt"));
        error(update("terms", first, termUpdate("ACTIVE", 0)), 409, "CONCURRENT_MODIFICATION");
        var changedDates = termUpdate("ACTIVE", 1); changedDates.put("endDate", "2027-05-01");
        error(update("terms", active, changedDates), 409, "INVALID_ACADEMIC_STATE");
        var otherBody = termBody(); otherBody.put("code", prefix + "OTHER");
        var other = create("terms", otherBody);
        var duplicate = termUpdate("PLANNED", 0); duplicate.put("code", prefix);
        error(update("terms", other, duplicate), 409, "ACADEMIC_RESOURCE_ALREADY_EXISTS");
        call(get(ROOT + "terms/" + id(other)), null).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(0))
                .andExpect(jsonPath("$.code").value(prefix + "OTHER"));
    }

    @Test
    void rejectsStaffInactiveAndMissingFacultyButAllowsCrossOrganizationFaculty() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        for (UUID invalid : List.of(member(PersonnelType.STAFF, PersonnelStatus.ACTIVE), member(PersonnelType.FACULTY, PersonnelStatus.INACTIVE), UUID.randomUUID())) {
            error(call(post(ROOT + "sections"), sectionBody(id(offering), invalid)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        }
        var anotherUnit = units.create(prefix + "X", "Other Unit", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE);
        UUID visitor = personnel.create(prefix + "VISIT", "Visitor", null, null, PersonnelType.FACULTY, anotherUnit.id(), PersonnelStatus.ACTIVE).id();
        var section = create("sections", sectionBody(id(offering), visitor));
        assertThat(section.get("facultyId").asText()).isEqualTo(visitor.toString());
        var badUpdate = sectionUpdate(section, "DRAFT", UUID.randomUUID(), 0);
        error(update("sections", section, badUpdate), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
    }

    @Test
    void enforcesUniqueOfferingAndScopedSectionCodeAndCurrentCourseAvailability() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        error(call(post(ROOT + "offerings"), Map.of("termId", id(term), "courseId", course)), 409, "ACADEMIC_RESOURCE_ALREADY_EXISTS");
        var section = create("sections", sectionBody(id(offering), null));
        error(call(post(ROOT + "sections"), sectionBody(id(offering), null)), 409, "ACADEMIC_RESOURCE_ALREADY_EXISTS");
        UUID otherCourse = catalog.createCourse(prefix + "C2", "Other", 3, unit, AcademicCatalogStatus.ACTIVE).id();
        var otherOffering = create("offerings", Map.of("termId", id(term), "courseId", otherCourse));
        create("sections", sectionBody(id(otherOffering), null));
        var c = catalog.course(course);
        catalog.updateCourse(course, c.code(), c.title(), c.credits(), unit, AcademicCatalogStatus.INACTIVE, c.rowVersion());
        error(call(post(ROOT + "sections"), sectionBody(id(offering), faculty)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        term = updateOk("terms", term, termUpdate("ACTIVE", 0));
        error(update("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        error(call(post(ROOT + "offerings"), Map.of("termId", id(term), "courseId", UUID.randomUUID())), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
    }

    @ParameterizedTest @ValueSource(strings = {"terms", "offerings", "sections"})
    void returnsUniformErrorsAndRejectsInvalidQueries(String resource) throws Exception {
        error(call(get(ROOT + resource + "/bad-id"), null), 400, "MALFORMED_REQUEST");
        error(call(get(ROOT + resource + "/" + UUID.randomUUID()), null), 404, "ACADEMIC_RESOURCE_NOT_FOUND");
        error(call(post(ROOT + resource), Map.of()), 400, "VALIDATION_FAILED");
        for (var query : List.of(Map.entry("page", "-1"), Map.entry("size", "101"), Map.entry("size", "0"),
                Map.entry("page", "2147483647"), Map.entry("sort", "id,asc"), Map.entry("sort", "code,bad"), Map.entry("status", "INVALID"))) {
            error(call(get(ROOT + resource).param(query.getKey(), query.getValue()), null), 400, "INVALID_QUERY_PARAMETER");
        }
    }

    @Test
    void filtersPaginatesAndSortsWithinDatabase() throws Exception {
        var term = create("terms", termBody());
        var nextBody = termBody(); nextBody.put("code", prefix + "NEXT");
        create("terms", nextBody);
        call(get(ROOT + "terms").param("q", prefix.toLowerCase(Locale.ROOT)).param("size", "1").param("sort", "code,desc"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].code").value(prefix + "NEXT"));
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        call(get(ROOT + "offerings").param("termId", id(term).toString()).param("courseId", course.toString()), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        create("sections", sectionBody(id(offering), null));
        var second = sectionBody(id(offering), faculty); second.put("code", "ZZ02"); second.put("capacity", 50);
        create("sections", second);
        call(get(ROOT + "sections").param("offeringId", id(offering).toString()).param("size", "1").param("sort", "capacity,desc"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].capacity").value(50));
    }

    @Test
    void serializesOfferingOpenAgainstTermClose() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        term = updateOk("terms", term, termUpdate("ACTIVE", 0));
        race("terms", term, termUpdate("CLOSED", 1), "offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0));
        var finalTerm = read(call(get(ROOT + "terms/" + id(term)), null));
        var finalOffering = read(call(get(ROOT + "offerings/" + id(offering)), null));
        assertThat(finalTerm.get("status").asText().equals("CLOSED") && finalOffering.get("status").asText().equals("OPEN")).isFalse();
    }

    @Test
    void serializesSectionOpenAgainstOfferingClose() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        var section = create("sections", sectionBody(id(offering), faculty));
        updateOk("terms", term, termUpdate("ACTIVE", 0));
        offering = updateOk("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0));
        race("offerings", offering, Map.of("status", "CLOSED", "expectedVersion", 1), "sections", section, sectionUpdate(section, "OPEN", faculty, 0));
        var finalOffering = read(call(get(ROOT + "offerings/" + id(offering)), null));
        var finalSection = read(call(get(ROOT + "sections/" + id(section)), null));
        assertThat(finalOffering.get("status").asText().equals("CLOSED") && finalSection.get("status").asText().equals("OPEN")).isFalse();
    }

    @Test
    void rejectsStaleOfferingAndSectionWritesAndRollsBackDuplicateSectionUpdate() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        var section = create("sections", sectionBody(id(offering), null));
        var nextBody = sectionBody(id(offering), faculty); nextBody.put("code", "OTHER");
        create("sections", nextBody);
        var withFaculty = updateOk("sections", section, sectionUpdate(section, "DRAFT", faculty, 0));
        error(update("sections", section, sectionUpdate(section, "DRAFT", null, 0)), 409, "CONCURRENT_MODIFICATION");
        var duplicate = sectionUpdate(section, "DRAFT", faculty, 1); duplicate.put("code", "other");
        error(update("sections", withFaculty, duplicate), 409, "ACADEMIC_RESOURCE_ALREADY_EXISTS");
        call(get(ROOT + "sections/" + id(section)), null).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1))
                .andExpect(jsonPath("$.code").value("CS01")).andExpect(jsonPath("$.facultyId").value(faculty.toString()));
        updateOk("terms", term, termUpdate("ACTIVE", 0));
        var opened = updateOk("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0));
        error(update("offerings", opened, Map.of("status", "CLOSED", "expectedVersion", 0)), 409, "CONCURRENT_MODIFICATION");
    }

    @Test
    void checksCurrentOrganizationAndFacultyStateButCanCloseHistoricalSections() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        var section = create("sections", sectionBody(id(offering), faculty));
        updateOk("terms", term, termUpdate("ACTIVE", 0));
        var openedOffering = updateOk("offerings", offering, Map.of("status", "OPEN", "expectedVersion", 0));
        var organization = units.get(unit);
        units.update(unit, organization.code(), organization.name(), organization.unitType(), OrganizationUnitStatus.INACTIVE, organization.rowVersion());
        error(update("sections", section, sectionUpdate(section, "OPEN", faculty, 0)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        error(call(post(ROOT + "sections"), sectionBody(id(offering), null)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        var current = units.get(unit);
        units.update(unit, current.code(), current.name(), current.unitType(), OrganizationUnitStatus.ACTIVE, current.rowVersion());
        var teacher = personnel.get(faculty);
        personnel.update(faculty, teacher.personnelNumber(), teacher.fullName(), teacher.email(), teacher.identityUserId(),
                teacher.personnelType(), teacher.organizationUnitId(), PersonnelStatus.INACTIVE, teacher.rowVersion());
        error(update("sections", section, sectionUpdate(section, "OPEN", faculty, 0)), 409, "ACADEMIC_REFERENCE_UNAVAILABLE");
        teacher = personnel.get(faculty);
        personnel.update(faculty, teacher.personnelNumber(), teacher.fullName(), teacher.email(), teacher.identityUserId(),
                teacher.personnelType(), teacher.organizationUnitId(), PersonnelStatus.ACTIVE, teacher.rowVersion());
        var opened = updateOk("sections", section, sectionUpdate(section, "OPEN", faculty, 0));
        teacher = personnel.get(faculty);
        personnel.update(faculty, teacher.personnelNumber(), teacher.fullName(), teacher.email(), teacher.identityUserId(),
                teacher.personnelType(), teacher.organizationUnitId(), PersonnelStatus.INACTIVE, teacher.rowVersion());
        updateOk("sections", opened, sectionUpdate(opened, "CLOSED", faculty, 1));
        updateOk("offerings", openedOffering, Map.of("status", "CLOSED", "expectedVersion", 1));
    }

    @Test
    void supportsCancellationAndRejectsCreationUnderCancelledParents() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        var section = create("sections", sectionBody(id(offering), null));
        var cancelledSection = updateOk("sections", section, sectionUpdate(section, "CANCELLED", null, 0));
        error(update("sections", cancelledSection, sectionUpdate(cancelledSection, "OPEN", faculty, 1)), 409, "INVALID_ACADEMIC_STATE");
        updateOk("offerings", offering, Map.of("status", "CANCELLED", "expectedVersion", 0));
        error(call(post(ROOT + "sections"), sectionBody(id(offering), null)), 409, "INVALID_ACADEMIC_STATE");
        updateOk("terms", term, termUpdate("CANCELLED", 0));
        error(call(post(ROOT + "offerings"), Map.of("termId", id(term), "courseId", course)), 409, "INVALID_ACADEMIC_STATE");
    }

    @Test
    void rejectsNonpositiveSectionCapacityIndependentlyOfOtherFields() throws Exception {
        var term = create("terms", termBody());
        var offering = create("offerings", Map.of("termId", id(term), "courseId", course));
        var body = sectionBody(id(offering), null); body.put("capacity", 0);
        error(call(post(ROOT + "sections"), body), 400, "VALIDATION_FAILED");
    }

    private void race(String leftKind, JsonNode left, Map<String, Object> leftBody, String rightKind, JsonNode right, Map<String, Object> rightBody) throws Exception {
        long auditBefore = jdbc.queryForObject("SELECT count(*) FROM academic_audit_events WHERE target_id IN (?, ?)", Long.class, id(left), id(right));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var request : List.of(put(ROOT + leftKind + "/" + id(left)).content(json.writeValueAsString(leftBody)),
                    put(ROOT + rightKind + "/" + id(right)).content(json.writeValueAsString(rightBody)))) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                    return call(request, null).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_audit_events WHERE target_id IN (?, ?)", Long.class, id(left), id(right)))
                    .isEqualTo(auditBefore + 1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private UUID member(PersonnelType type, PersonnelStatus status) {
        return personnel.create(UUID.randomUUID().toString().substring(0, 8), "Faculty Profile", null, null, type, unit, status).id();
    }
    private String token(RoleCode role) {
        var user = users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Academic Admin", passwords.encode("test-password"),
                AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
        return tokens.accessToken(user);
    }
    private UUID id(JsonNode value) { return UUID.fromString(value.get("id").asText()); }
    private Map<String, Object> termBody() { return new LinkedHashMap<>(Map.of("code", prefix, "name", "Semester", "startDate", "2027-01-01", "endDate", "2027-04-30")); }
    private Map<String, Object> termUpdate(String status, long version) { var value = termBody(); value.put("status", status); value.put("expectedVersion", version); return value; }
    private Map<String, Object> sectionBody(UUID offering, UUID faculty) {
        var value = new LinkedHashMap<String, Object>(); value.put("offeringId", offering); value.put("code", "CS01"); value.put("capacity", 30); value.put("facultyId", faculty); return value;
    }
    private Map<String, Object> sectionUpdate(JsonNode section, String status, UUID faculty, long version) {
        var value = sectionBody(UUID.fromString(section.get("offeringId").asText()), faculty); value.put("status", status); value.put("expectedVersion", version); return value;
    }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private JsonNode create(String resource, Object body) throws Exception { return read(call(post(ROOT + resource), body).andExpect(status().isCreated())); }
    private ResultActions update(String resource, JsonNode old, Object body) throws Exception { return call(put(ROOT + resource + "/" + id(old)), body); }
    private JsonNode updateOk(String resource, JsonNode old, Object body) throws Exception { return read(update(resource, old, body).andExpect(status().isOk())); }
    private void error(ResultActions result, int expected, String code) throws Exception {
        result.andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.path").exists()).andExpect(jsonPath("$.timestamp").exists());
    }
}
