package com.campus.dormitory.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.dormitory.application.DormitoryInventoryService;
import com.campus.dormitory.domain.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class DormitoryInventoryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DormitoryInventoryService service;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired InventoryRepository inventory;
    @Autowired PlatformTransactionManager transactions;
    UUID actor, building, room;
    String admin, user, prefix;
    static final String ROOT = "/api/v1/admin/dormitory/";

    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var account = account(RoleCode.ADMIN);
        actor = account.id(); admin = tokens.accessToken(account); user = tokens.accessToken(account(RoleCode.USER));
        building = service.create(actor, InventoryKind.BUILDING, null, prefix, "Building").id();
        room = service.create(actor, InventoryKind.ROOM, building, prefix, "Room").id();
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void protectsAllOperationsAndRejectsBadRequestsWithoutWrites(String route) throws Exception {
        long count = audits();
        var before = rows(route);
        for (String token : List.of("", user)) {
            for (var request : List.of(get(ROOT + route), get(ROOT + route + "/" + UUID.randomUUID()), post(ROOT + route), put(ROOT + route + "/" + UUID.randomUUID()))) {
                if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
                mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().is(token.isEmpty() ? 401 : 403));
            }
        }
        call(post(ROOT + route), Map.of()).andExpect(status().isBadRequest());
        call(get(ROOT + route + "/bad-id"), null).andExpect(status().isBadRequest());
        call(get(ROOT + route + "/" + UUID.randomUUID()), null).andExpect(status().isNotFound());
        for (var invalid : List.of(Map.entry("page", "-1"), Map.entry("page", "2147483647"), Map.entry("size", "0"),
                Map.entry("size", "101"), Map.entry("sort", "id,asc"), Map.entry("sort", "code,invalid"),
                Map.entry("status", "UNKNOWN"), Map.entry("q", "X".repeat(101)))) {
            call(get(ROOT + route).param(invalid.getKey(), invalid.getValue()), null).andExpect(status().isBadRequest());
        }
        assertThat(rows(route)).isEqualTo(before); assertThat(audits()).isEqualTo(count);
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void createsUpdatesReadsAndRejectsStaleDuplicateWithCorrectAudit(String route) throws Exception {
        long before = audits();
        var body = body(route, prefix + "C");
        body.put("actorUserId", UUID.randomUUID());
        var created = read(call(post(ROOT + route), body).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        UUID id = id(created);
        assertThat(created.get("rowVersion").asLong()).isZero();
        assertThat(read(call(get(ROOT + route + "/" + id), null).andExpect(status().isOk()))).isEqualTo(created);
        call(post(ROOT + route), body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DORMITORY_CODE_ALREADY_EXISTS"));
        var update = update(created, "Updated", "ACTIVE");
        update.put("parentId", UUID.randomUUID()); // Not a PUT field; parent stays immutable.
        var changed = read(call(put(ROOT + route + "/" + id), update).andExpect(status().isOk()));
        assertThat(changed.get("rowVersion").asLong()).isEqualTo(1);
        assertThat(changed.get("parentId")).isEqualTo(created.get("parentId"));
        assertThat(changed.get("createdAt")).isEqualTo(created.get("createdAt"));
        assertThat(read(call(get(ROOT + route + "/" + id), null).andExpect(status().isOk()))).isEqualTo(changed);
        call(put(ROOT + route + "/" + id), update).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(get(ROOT + route + "/" + id), null).andExpect(status().isOk());
        call(get(ROOT + route).param("q", prefix), null).andExpect(status().isOk());
        assertThat(audits()).isEqualTo(before + 2);
        for (long version : List.of(0L, 1L)) {
            var event = jdbc.queryForMap("SELECT * FROM dormitory_audit_events WHERE target_id = ? AND resource_version = ?", id, version);
            assertThat(event).containsEntry("actor_user_id", actor).containsEntry("resource_type", kind(route).name())
                    .containsEntry("action", version == 0 ? "CREATED" : "UPDATED");
            assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status", "ACTIVE"));
            assertThat(event.get("occurred_at")).isNotNull();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void realAuditFailureRollsBackCreateAndUpdate(String route) throws Exception {
        var before = rows(route); long count = audits();
        var body = body(route, prefix + "RB");
        rejectAudit();
        try {
            call(post(ROOT + route), body).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(rows(route)).isEqualTo(before); assertThat(audits()).isEqualTo(count);
        } finally { allowAudit(); }
        var created = read(call(post(ROOT + route), body).andExpect(status().isCreated()));
        before = rows(route); count = audits(); rejectAudit();
        try {
            call(put(ROOT + route + "/" + id(created)), update(created, "Changed", "INACTIVE")).andExpect(status().isInternalServerError());
            assertThat(rows(route)).isEqualTo(before); assertThat(audits()).isEqualTo(count);
        } finally { allowAudit(); }
    }

    @Test void protectsHierarchyAndScopedCodesWithoutCascades() throws Exception {
        var bed = service.create(actor, InventoryKind.BED, room, "BD", "Bed");
        assertThatThrownBy(() -> service.update(actor, InventoryKind.BUILDING, building, prefix, "Building", InventoryStatus.INACTIVE, 0))
                .isInstanceOf(DormitoryInventoryService.InvalidStateException.class);
        assertThatThrownBy(() -> service.update(actor, InventoryKind.ROOM, room, prefix, "Room", InventoryStatus.INACTIVE, 0))
                .isInstanceOf(DormitoryInventoryService.InvalidStateException.class);
        service.update(actor, InventoryKind.BED, bed.id(), "BD", "Bed", InventoryStatus.INACTIVE, 0);
        service.update(actor, InventoryKind.ROOM, room, prefix, "Room", InventoryStatus.INACTIVE, 0);
        service.update(actor, InventoryKind.BUILDING, building, prefix, "Building", InventoryStatus.INACTIVE, 0);
        assertThatThrownBy(() -> service.create(actor, InventoryKind.ROOM, building, "R2", "Room"))
                .isInstanceOf(DormitoryInventoryService.ReferenceUnavailableException.class);
        assertThatThrownBy(() -> service.create(actor, InventoryKind.BED, room, "B2", "Bed"))
                .isInstanceOf(DormitoryInventoryService.ReferenceUnavailableException.class);
        assertThatThrownBy(() -> service.update(actor, InventoryKind.BED, bed.id(), "BD", "Bed", InventoryStatus.ACTIVE, 1))
                .isInstanceOf(DormitoryInventoryService.ReferenceUnavailableException.class);
        assertThat(service.get(InventoryKind.BED, bed.id()).status()).isEqualTo(InventoryStatus.INACTIVE);
        var other = service.create(actor, InventoryKind.BUILDING, null, prefix + "OTHER", "Other");
        var otherRoom = service.create(actor, InventoryKind.ROOM, other.id(), prefix, "Room");
        assertThat(service.create(actor, InventoryKind.BED, otherRoom.id(), "BD", "Bed").parentId()).isEqualTo(otherRoom.id());
        call(post(ROOT + "rooms"), Map.of("code", "RR", "name", "Room", "parentId", UUID.randomUUID())).andExpect(status().isNotFound());
        call(post(ROOT + "buildings"), Map.of("code", "BB", "name", "Building", "parentId", room)).andExpect(status().isBadRequest());
        call(post(ROOT + "beds"), Map.of("code", "BB", "name", "Bed")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void searchesLiteralTextWithStablePaginationAndApprovedSorts(String route) throws Exception {
        var first = body(route, prefix + "A"); first.put("name", prefix + "%_!😀");
        var second = body(route, prefix + "B"); second.put("name", prefix + "%_!😀");
        var one = read(call(post(ROOT + route), first).andExpect(status().isCreated()));
        var two = read(call(post(ROOT + route), second).andExpect(status().isCreated()));
        for (String field : List.of("code", "name", "status", "createdAt", "updatedAt")) {
            for (String direction : List.of("asc", "desc")) {
                call(get(ROOT + route).param("q", "%_!😀").param("sort", field + "," + direction).param("size", "1"), null)
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
            }
        }
        jdbc.update("UPDATE " + table(route) + " SET created_at = TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE id IN (?, ?)", id(one), id(two));
        var page0 = read(call(get(ROOT + route).param("q", "%_!😀").param("sort", "createdAt,desc").param("size", "1"), null));
        var page1 = read(call(get(ROOT + route).param("q", "%_!😀").param("sort", "createdAt,desc").param("size", "1").param("page", "1"), null));
        var ordered = jdbc.queryForList("SELECT id FROM " + table(route) + " WHERE id IN (?, ?) ORDER BY id", UUID.class, id(one), id(two));
        assertThat(page0.get("content").get(0).get("id").asText()).isEqualTo(ordered.get(0).toString());
        assertThat(page1.get("content").get(0).get("id").asText()).isEqualTo(ordered.get(1).toString());
        call(put(ROOT + route + "/" + id(one)), update(one, prefix + "%_!😀", "INACTIVE")).andExpect(status().isOk());
        var filtered = get(ROOT + route).param("q", "%_!😀").param("status", "INACTIVE");
        if (!route.equals("buildings")) filtered.param("parentId", (route.equals("rooms") ? building : room).toString());
        call(filtered, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        call(get(ROOT + route).param("page", "2147483647").param("size", "1"), null).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void onlyOneCompetingUpdateCommitsAndAudits(String route) throws Exception {
        var created = read(call(post(ROOT + route), body(route, prefix + "RACE")).andExpect(status().isCreated()));
        long before = audits();
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                String content = json.writeValueAsString(update(created, "Changed " + i, "ACTIVE"));
                futures.add(pool.submit(() -> {
                    ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return mvc.perform(put(ROOT + route + "/" + id(created)).header("Authorization", "Bearer " + admin)
                            .contentType("application/json").content(content)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(service.get(kind(route), id(created)).rowVersion()).isEqualTo(1);
            assertThat(audits()).isEqualTo(before + 1);
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @ValueSource(strings = {"rooms", "beds"})
    void parentDeactivationAndChildCreationCannotBothCommit(String child) throws Exception {
        InventoryKind parentKind = child.equals("rooms") ? InventoryKind.BUILDING : InventoryKind.ROOM;
        UUID parent = service.create(actor, parentKind, parentKind == InventoryKind.BUILDING ? null : building, prefix + "EMPTY", "Empty").id();
        var value = service.get(parentKind, parent);
        long before = audits();
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var close = pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return catchThrowable(() -> service.update(actor, parentKind, parent, value.code(), value.name(), InventoryStatus.INACTIVE, 0));
            });
            var create = pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return catchThrowable(() -> service.create(actor, kind(child), parent, "NEW", "New Child"));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = Arrays.asList(close.get(20, TimeUnit.SECONDS), create.get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(Objects::isNull).hasSize(1);
            assertThat(results).filteredOn(Objects::nonNull).singleElement().satisfies(failure ->
                    assertThat(failure).isInstanceOfAny(DormitoryInventoryService.InvalidStateException.class, DormitoryInventoryService.ReferenceUnavailableException.class));
            assertThat(audits()).isEqualTo(before + 1);
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @ValueSource(strings = {"rooms", "beds"})
    void productionParentLockBlocksChildCreationUntilReleased(String child) throws Exception {
        InventoryKind parentKind = child.equals("rooms") ? InventoryKind.BUILDING : InventoryKind.ROOM;
        UUID parent = child.equals("rooms") ? building : room;
        var pool = Executors.newSingleThreadExecutor();
        long before = audits();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(holder -> {
                inventory.lock(parentKind, parent);
                var blocked = pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(contender -> {
                    jdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    service.create(actor, kind(child), parent, prefix + "BLOCK", "Blocked Child");
                })));
                try {
                    Throwable failure = blocked.get(10, TimeUnit.SECONDS);
                    assertThat(failure).isNotNull();
                    while (failure.getCause() != null) failure = failure.getCause();
                    assertThat(failure).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) failure).getSQLState()).isEqualTo("55P03");
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            assertThat(audits()).isEqualTo(before);
            assertThat(service.create(actor, kind(child), parent, prefix + "BLOCK", "Released Child").status()).isEqualTo(InventoryStatus.ACTIVE);
            assertThat(audits()).isEqualTo(before + 1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @ValueSource(strings = {"buildings", "rooms", "beds"})
    void duplicateUpdatePreservesFullRowsAndAudit(String route) throws Exception {
        var first = read(call(post(ROOT + route), body(route, prefix + "FIRST")).andExpect(status().isCreated()));
        var second = read(call(post(ROOT + route), body(route, prefix + "SECOND")).andExpect(status().isCreated()));
        long count = audits(); var before = rows(route);
        var body = update(second, "Duplicate", "ACTIVE"); body.put("code", first.get("code").asText().toLowerCase(Locale.ROOT));
        call(put(ROOT + route + "/" + id(second)), body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DORMITORY_CODE_ALREADY_EXISTS"));
        assertThat(rows(route)).isEqualTo(before); assertThat(audits()).isEqualTo(count);
    }

    @Test void generatedOpenApiDeclaresBearerAndVersionedUpdate() throws Exception {
        var spec = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        var collection = spec.path("paths").path(ROOT + "{resource}");
        var item = spec.path("paths").path(ROOT + "{resource}/{id}");
        for (var operation : List.of(collection.path("get"), collection.path("post"), item.path("get"), item.path("put"))) {
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
        }
        // OpenAPI can suffix similarly named records; resolve the actual request reference.
        String reference = item.path("put").path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText();
        assertThat(reference).endsWith("/DormitoryInventoryUpdateRequest");
        var update = spec.path("components").path("schemas").path(reference.substring(reference.lastIndexOf('/') + 1));
        assertThat(update.path("properties").has("expectedVersion")).isTrue();
        assertThat(update.path("required").toString()).contains("\"expectedVersion\"");
        assertThat(update.path("properties").has("parentId")).isFalse();
        assertThat(update.path("properties").has("code")).isTrue();
        assertThat(update.path("properties").has("name")).isTrue();
        assertThat(update.path("properties").has("status")).isTrue();
        var create = spec.path("components").path("schemas").path("DormitoryInventoryCreateRequest");
        assertThat(create.path("properties").has("parentId")).isTrue();
        assertThat(create.path("properties").has("expectedVersion")).isFalse();
    }

    private UserAccount account(RoleCode role) {
        return users.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Inventory Admin",
                passwords.encode("valid-password"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now()));
    }
    private Map<String, Object> body(String route, String code) {
        var body = new LinkedHashMap<String, Object>(); body.put("code", code); body.put("name", "Inventory");
        if (!route.equals("buildings")) body.put("parentId", route.equals("rooms") ? building : room);
        return body;
    }
    private Map<String, Object> update(JsonNode item, String name, String status) {
        return new LinkedHashMap<>(Map.of("code", item.get("code").asText(), "name", name, "status", status, "expectedVersion", item.get("rowVersion").asLong()));
    }
    private InventoryKind kind(String route) { return switch (route) { case "buildings" -> InventoryKind.BUILDING; case "rooms" -> InventoryKind.ROOM; default -> InventoryKind.BED; }; }
    private UUID id(JsonNode value) { return UUID.fromString(value.get("id").asText()); }
    private String table(String route) { return "dormitory_" + route; }
    private List<Map<String, Object>> rows(String route) { return jdbc.queryForList("SELECT * FROM " + table(route) + " ORDER BY id"); }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM dormitory_audit_events WHERE actor_user_id = ?", Long.class, actor); }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + admin).contentType("application/json");
        if (body != null) request.content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_dormitory_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_dormitory_audit_test BEFORE INSERT ON dormitory_audit_events FOR EACH ROW EXECUTE FUNCTION reject_dormitory_audit_test()");
    }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_dormitory_audit_test ON dormitory_audit_events"); }
}
