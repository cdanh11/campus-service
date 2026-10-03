package com.campus.academic.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@PostgresApplicationTest
class AdminAcademicCatalogControllerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired OrganizationUnitManagementService units;
    String admin;
    UUID unit;
    String prefix;

    @BeforeEach
    void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        admin = tokens.accessToken(account(UUID.randomUUID() + "@campus.example", RoleCode.ADMIN));
        unit = units.create(prefix, "Academic", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.ACTIVE).id();
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void restrictsAllCatalogOperationsToAdministrators(String kind) throws Exception {
        String user = tokens.accessToken(account(UUID.randomUUID() + "@campus.example", RoleCode.USER));
        String path = path(kind);
        for (String token : List.of("", user)) {
            for (var request : List.of(get(path), get(path + "/" + UUID.randomUUID()), post(path), put(path + "/" + UUID.randomUUID()))) {
                if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
                mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().is(token.isEmpty() ? 401 : 403));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void createsReadsUpdatesAndRejectsStaleOrDuplicateUpdates(String kind) throws Exception {
        var first = create(kind, " " + prefix + "01 ", " Original ", "ACTIVE");
        String id = first.get("id").asText();
        assertThat(first.get("code").asText()).isEqualTo(prefix + "01");
        assertThat(first.get(label(kind)).asText()).isEqualTo("Original");
        assertThat(first.get("rowVersion").asLong()).isZero();
        var persisted = read(perform(get(path(kind) + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(prefix + "01")));
        var update = body(kind, prefix + "02", "Updated", "INACTIVE");
        update.put("expectedVersion", 0);
        var changed = read(perform(put(path(kind) + "/" + id).content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1)));
        assertThat(changed.get("createdAt")).isEqualTo(persisted.get("createdAt"));
        error(perform(put(path(kind) + "/" + id).content(json.writeValueAsString(update))), 409, "CONCURRENT_MODIFICATION");
        create(kind, prefix + "03", "Other", "ACTIVE");
        update.put("code", prefix + "03");
        update.put("expectedVersion", 1);
        error(perform(put(path(kind) + "/" + id).content(json.writeValueAsString(update))), 409,
                kind.equals("programs") ? "PROGRAM_CODE_ALREADY_EXISTS" : "COURSE_CODE_ALREADY_EXISTS");
        perform(get(path(kind) + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(prefix + "02"))
                .andExpect(jsonPath("$.rowVersion").value(1)).andExpect(jsonPath("$.status").value("INACTIVE"));
        error(perform(post(path(kind)).content(json.writeValueAsString(body(kind, (prefix + "03").toLowerCase(Locale.ROOT), "Duplicate", "ACTIVE")))),
                409, kind.equals("programs") ? "PROGRAM_CODE_ALREADY_EXISTS" : "COURSE_CODE_ALREADY_EXISTS");
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void returnsUniformErrorsForMalformedMissingAndUnavailableReferences(String kind) throws Exception {
        error(perform(get(path(kind) + "/not-a-uuid")), 400, "MALFORMED_REQUEST");
        String missingCode = kind.equals("programs") ? "ACADEMIC_PROGRAM_NOT_FOUND" : "ACADEMIC_COURSE_NOT_FOUND";
        error(perform(get(path(kind) + "/" + UUID.randomUUID())), 404, missingCode);
        var value = body(kind, prefix + "01", "Valid", "ACTIVE");
        value.put("organizationUnitId", UUID.randomUUID());
        error(perform(post(path(kind)).content(json.writeValueAsString(value))), 409, "ORGANIZATION_UNIT_UNAVAILABLE");
        var inactive = units.create(prefix + "I", "Inactive", OrganizationUnitType.DEPARTMENT, OrganizationUnitStatus.INACTIVE);
        value.put("organizationUnitId", inactive.id());
        error(perform(post(path(kind)).content(json.writeValueAsString(value))), 409, "ORGANIZATION_UNIT_UNAVAILABLE");
        var existing = create(kind, prefix + "02", "Existing", "ACTIVE");
        value.put("expectedVersion", 0);
        error(perform(put(path(kind) + "/" + existing.get("id").asText()).content(json.writeValueAsString(value))), 409, "ORGANIZATION_UNIT_UNAVAILABLE");
        value.put("organizationUnitId", unit);
        error(perform(put(path(kind) + "/" + UUID.randomUUID()).content(json.writeValueAsString(value))), 404, missingCode);
        error(perform(post(path(kind)).content("{broken")), 400, "MALFORMED_REQUEST");
        value.put("status", "INVALID");
        error(perform(post(path(kind)).content(json.writeValueAsString(value))), 400, "MALFORMED_REQUEST");
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void validatesEachFieldAndRequiresNonnegativeUpdateVersion(String kind) throws Exception {
        for (var invalid : List.of(Map.entry("code", "x"), Map.entry("code", "X".repeat(33)),
                Map.entry(label(kind), "x"), Map.entry(label(kind), "X".repeat(161)), Map.entry(label(kind), " \t\n\r\u000b\f"))) {
            var value = body(kind, prefix + "01", "Valid", "ACTIVE");
            value.put(invalid.getKey(), invalid.getValue());
            error(perform(post(path(kind)).content(json.writeValueAsString(value))), 400, "VALIDATION_FAILED");
        }
        var existing = create(kind, prefix + "02", "Existing", "ACTIVE");
        var update = body(kind, prefix + "03", "Changed", "ACTIVE");
        String target = path(kind) + "/" + existing.get("id").asText();
        error(perform(put(target).content(json.writeValueAsString(update))), 400, "VALIDATION_FAILED");
        update.put("expectedVersion", -1);
        error(perform(put(target).content(json.writeValueAsString(update))), 400, "VALIDATION_FAILED");
        update.put("expectedVersion", 0);
        update.put("status", null);
        error(perform(put(target).content(json.writeValueAsString(update))), 400, "VALIDATION_FAILED");
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void searchesFiltersAndPaginatesWithDeterministicSorting(String kind) throws Exception {
        create(kind, prefix + "01", "Shared title", "ACTIVE");
        create(kind, prefix + "02", "Shared title", "ACTIVE");
        create(kind, prefix + "03", "Hidden title", "INACTIVE");
        perform(get(path(kind)).param("q", prefix.toLowerCase(Locale.ROOT)).param("status", "ACTIVE").param("size", "1").param("sort", "code,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].code").value(prefix + "02"));
        perform(get(path(kind)).param("q", prefix).param("status", "ACTIVE").param("size", "1").param("page", "1").param("sort", "code,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].code").value(prefix + "01"));
        perform(get(path(kind)).param("q", prefix).param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        var first = read(perform(get(path(kind)).param("q", prefix).param("sort", label(kind) + ",asc")));
        var second = read(perform(get(path(kind)).param("q", prefix).param("sort", label(kind) + ",asc")));
        assertThat(first).isEqualTo(second);
        var defaults = read(perform(get(path(kind))));
        assertThat(defaults.get("page").asInt()).isZero();
        assertThat(defaults.get("size").asInt()).isEqualTo(20);
        perform(get(path(kind)).param("q", "_%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        create(kind, prefix + "04", prefix + " findme", "ACTIVE");
        perform(get(path(kind)).param("q", prefix.toLowerCase(Locale.ROOT) + " FINDME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void rejectsInvalidSearchParameters(String kind) throws Exception {
        for (var invalid : List.of(Map.entry("page", "-1"), Map.entry("page", "2147483647"), Map.entry("size", "0"), Map.entry("size", "101"),
                Map.entry("status", "INVALID"), Map.entry("sort", "id,asc"), Map.entry("sort", "code,invalid"),
                Map.entry("sort", "code"), Map.entry("q", "x".repeat(101)), Map.entry("sort", label(kind.equals("programs") ? "courses" : "programs") + ",asc"))) {
            error(perform(get(path(kind)).param(invalid.getKey(), invalid.getValue())), 400, "INVALID_QUERY_PARAMETER");
        }
        error(perform(get(path(kind)).param("page", "text")), 400, "MALFORMED_REQUEST");
    }

    @ParameterizedTest @ValueSource(ints = {0, 31})
    void rejectsInvalidCreditsWithoutOtherInvalidFields(int credits) throws Exception {
        var value = body("courses", prefix + "01", "Valid Course", "ACTIVE");
        value.put("credits", credits);
        error(perform(post(path("courses")).content(json.writeValueAsString(value))), 400, "VALIDATION_FAILED");
    }

    @ParameterizedTest @ValueSource(strings = {"programs", "courses"})
    void acceptsExactlyOneOfTwoCompetingUpdates(String kind) throws Exception {
        var value = create(kind, prefix + "01", "Original", "ACTIVE");
        String target = path(kind) + "/" + value.get("id").asText();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                var update = body(kind, prefix + "0" + (index + 2), "Competing " + index, "ACTIVE");
                update.put("expectedVersion", 0);
                String content = json.writeValueAsString(update);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    return perform(put(target).content(content)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            perform(get(target)).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private String path(String kind) { return "/api/v1/admin/academic/" + kind; }
    private String label(String kind) { return kind.equals("programs") ? "name" : "title"; }
    private Map<String, Object> body(String kind, String code, String text, String status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("code", code); value.put(label(kind), text); value.put("organizationUnitId", unit); value.put("status", status);
        if (kind.equals("courses")) value.put("credits", 3);
        return value;
    }
    private JsonNode create(String kind, String code, String text, String status) throws Exception {
        return read(perform(post(path(kind)).content(json.writeValueAsString(body(kind, code, text, status)))).andExpect(status().isCreated()));
    }
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON));
    }
    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private void error(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.timestamp").exists()).andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.path").exists());
    }
    private UserAccount account(String email, RoleCode role) {
        return users.save(UserAccount.create(UUID.randomUUID(), email, "Catalog User", passwords.encode("valid-password"),
                AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }
}
