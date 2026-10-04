package com.campus.library.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import com.campus.library.application.LibraryService;
import com.campus.library.domain.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.organization.application.OrganizationUnitManagementService;
import com.campus.organization.domain.*;
import com.campus.student.application.StudentManagementService;
import com.campus.student.domain.StudentStatus;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class LibraryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LibraryService service;
    @Autowired LibraryRepository repository;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService organizations;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    static final String ROOT = "/api/v1/admin/library";
    UUID actor, student, otherStudent; String admin, user, prefix; BookTitle title; BookCopy copy;
    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        var account = account(RoleCode.ADMIN); actor = account.id(); admin = tokens.accessToken(account);
        user = tokens.accessToken(account(RoleCode.USER));
        var unit = organizations.create(prefix, "Library unit", OrganizationUnitType.FACULTY, OrganizationUnitStatus.ACTIVE).id();
        student = students.create("S" + prefix, "Library Student", null, null, unit, StudentStatus.ACTIVE).id();
        otherStudent = students.create("T" + prefix, "Other Student", null, null, unit, StudentStatus.ACTIVE).id();
        title = service.createTitle(actor, prefix + "-TITLE", "Book title", "Book author");
        copy = service.createCopy(actor, title.id(), prefix + "-COPY");
    }
    @Test void enforcesAdminOnEveryOperationAndDeclaresDistinctBearerSchemas() throws Exception {
        UUID loan = UUID.randomUUID();
        for (String resource : List.of("titles", "copies", "loans")) {
            UUID id = resource.equals("titles") ? title.id() : resource.equals("copies") ? copy.id() : loan;
            for (var request : List.of(post(ROOT + "/" + resource), get(ROOT + "/" + resource), get(ROOT + "/" + resource + "/" + id),
                    put(ROOT + "/" + resource + "/" + id + (resource.equals("loans") ? "/return" : "")))) {
                call(request, null, "").andExpect(status().isUnauthorized());
                call(request, null, user).andExpect(status().isForbidden());
            }
        }
        var spec = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for (String path : List.of("/titles", "/titles/{id}", "/copies", "/copies/{id}", "/loans", "/loans/{id}", "/loans/{id}/return"))
            spec.path("paths").path(ROOT + path).elements().forEachRemaining(operation -> assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue());
        for (String schema : List.of("LibraryTitleCreate", "LibraryTitleUpdate", "LibraryCopyCreate", "LibraryCopyUpdate", "LibraryLoanCreate", "LibraryLoanReturn", "LibraryTitlePage", "LibraryCopyPage", "LibraryLoanPage"))
            assertThat(spec.path("components").path("schemas").has(schema)).isTrue();
    }
    @Test void createsCatalogAndRetainsLoanHistoryPrecisionAndServerControlledFields() throws Exception {
        var created = read(call(post(ROOT + "/titles"), Map.of("code", prefix + "-NEW", "title", "😀".repeat(160), "author", " Nguyễn Văn "), admin)
                .andExpect(status().isCreated()).andExpect(header().exists("Location")));
        assertThat(created).isEqualTo(read(call(get(ROOT + "/titles/" + created.path("id").asText()), null, admin).andExpect(status().isOk())));
        call(put(ROOT + "/titles/" + title.id()), titleUpdate(LibraryStatus.ACTIVE, 0), admin).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        call(put(ROOT + "/copies/" + copy.id()), copyUpdate(LibraryStatus.ACTIVE, 0), admin).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        var borrowed = read(call(post(ROOT + "/loans"), Map.of("copyId", copy.id(), "studentId", student, "actor", UUID.randomUUID(), "dueAt", "2000-01-01T00:00:00Z"), admin).andExpect(status().isCreated()));
        UUID id = UUID.fromString(borrowed.path("id").asText());
        assertThat(Instant.parse(borrowed.path("dueAt").asText())).isEqualTo(Instant.parse(borrowed.path("borrowedAt").asText()).plus(14, ChronoUnit.DAYS));
        assertThat(borrowed).isEqualTo(read(call(get(ROOT + "/loans/" + id), null, admin).andExpect(status().isOk())));
        var returned = read(call(put(ROOT + "/loans/" + id + "/return"), Map.of("expectedVersion", 0), admin).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1)));
        for (String field : List.of("id", "copyId", "studentId", "borrowedAt", "dueAt", "createdAt")) assertThat(returned.path(field)).isEqualTo(borrowed.path(field));
        var next = service.borrow(actor, copy.id(), student); assertThat(next.id()).isNotEqualTo(id);
        assertThat(jdbc.queryForList("SELECT actor_user_id FROM library_audit_events WHERE target_id=?", UUID.class, id)).containsOnly(actor);
        assertThat(jdbc.queryForList("SELECT action FROM library_audit_events WHERE target_id=? ORDER BY occurred_at", String.class, id)).containsExactly("BORROWED", "RETURNED");
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM library_audit_events WHERE target_id=? AND action='RETURNED'", String.class, id)).isEqualTo("{\"status\": \"RETURNED\"}");
    }
    @Test void rejectsInvalidDuplicateMissingInactiveAndStaleInputsWithoutWrites() throws Exception {
        var before = snapshot();
        for (String invalid : List.of("a", " \t\n\r\u000b\f", "😀".repeat(161)))
            call(post(ROOT + "/titles"), Map.of("code", prefix + "-BAD", "title", invalid, "author", "Author"), admin).andExpect(status().isBadRequest());
        call(post(ROOT + "/titles"), Map.of("code", title.code().toLowerCase(Locale.ROOT), "title", "Title", "author", "Author"), admin).andExpect(status().isConflict());
        call(post(ROOT + "/copies"), Map.of("titleId", title.id(), "code", copy.code().toLowerCase(Locale.ROOT)), admin).andExpect(status().isConflict());
        call(post(ROOT + "/copies"), Map.of("titleId", UUID.randomUUID(), "code", prefix + "-BAD"), admin).andExpect(status().isNotFound());
        call(post(ROOT + "/loans"), Map.of("copyId", copy.id(), "studentId", UUID.randomUUID()), admin).andExpect(status().isConflict());
        call(post(ROOT + "/loans"), Map.of("copyId", UUID.randomUUID(), "studentId", student), admin).andExpect(status().isNotFound());
        for (String resource : List.of("titles", "copies", "loans")) call(get(ROOT + "/" + resource + "/" + UUID.randomUUID()), null, admin).andExpect(status().isNotFound());
        call(put(ROOT + "/titles/" + title.id()), titleUpdate(LibraryStatus.ACTIVE, 1), admin).andExpect(status().isConflict());
        call(put(ROOT + "/copies/" + copy.id()), copyUpdate(LibraryStatus.ACTIVE, 1), admin).andExpect(status().isConflict());
        assertThat(snapshot()).isEqualTo(before);
        service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.INACTIVE, 0);
        assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class);
        service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.ACTIVE, 1);
        service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.INACTIVE, 0);
        assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class);
        assertThatThrownBy(() -> service.createCopy(actor, title.id(), prefix + "-BAD")).isInstanceOf(LibraryService.UnavailableReferenceException.class);
        service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.ACTIVE, 1);
        jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?", student);
        assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class);
    }
    @Test void rejectsAllCoercedVersionBodiesOnEveryUpdate() throws Exception {
        var loan = service.borrow(actor, copy.id(), student); var before = snapshot();
        for (Object value : List.of(0.5, "0", false, new java.math.BigInteger("9223372036854775808"), -1)) {
            var titleBody = titleUpdate(LibraryStatus.ACTIVE, 0); titleBody.put("expectedVersion", value);
            var copyBody = copyUpdate(LibraryStatus.ACTIVE, 0); copyBody.put("expectedVersion", value);
            call(put(ROOT + "/titles/" + title.id()), titleBody, admin).andExpect(status().isBadRequest());
            call(put(ROOT + "/copies/" + copy.id()), copyBody, admin).andExpect(status().isBadRequest());
            call(put(ROOT + "/loans/" + loan.id() + "/return"), Map.of("expectedVersion", value), admin).andExpect(status().isBadRequest());
        }
        call(put(ROOT + "/loans/" + loan.id() + "/return"), Map.of(), admin).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void returnIsAllowedAfterDeactivationAndOverdueButNeverTwiceOrWithOldVersion() throws Exception {
        var loan = service.borrow(actor, copy.id(), student);
        jdbc.update("UPDATE library_loans SET borrowed_at=now()-interval '30 days',due_at=now()-interval '16 days' WHERE id=?", loan.id());
        var original = service.loan(loan.id());
        service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.INACTIVE, 0);
        service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.INACTIVE, 0);
        jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?", student);
        var returned = service.returnBook(actor, loan.id(), 0);
        assertThat(returned.borrowedAt()).isEqualTo(original.borrowedAt()); assertThat(returned.dueAt()).isEqualTo(original.dueAt());
        call(put(ROOT + "/loans/" + loan.id() + "/return"), Map.of("expectedVersion", 0), admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ROOT + "/loans/" + loan.id() + "/return"), Map.of("expectedVersion", 1), admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_LIBRARY_STATE"));
    }
    @Test void auditFailureRollsBackAllSixMutationsIncludingVersionsAndTimestamps() throws Exception {
        var loan = service.borrow(actor, copy.id(), student); var free = service.createCopy(actor, title.id(), prefix + "-FREE");
        var before = snapshot();
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_library_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_library_audit_test BEFORE INSERT ON library_audit_events FOR EACH ROW EXECUTE FUNCTION reject_library_audit_test()");
        try {
            call(post(ROOT + "/titles"), Map.of("code", prefix + "-ROLL", "title", "Title", "author", "Author"), admin).andExpect(status().isInternalServerError());
            call(put(ROOT + "/titles/" + title.id()), titleUpdate(LibraryStatus.INACTIVE, 0), admin).andExpect(status().isInternalServerError());
            call(post(ROOT + "/copies"), Map.of("titleId", title.id(), "code", prefix + "-ROLL"), admin).andExpect(status().isInternalServerError());
            call(put(ROOT + "/copies/" + copy.id()), copyUpdate(LibraryStatus.INACTIVE, 0), admin).andExpect(status().isInternalServerError());
            call(post(ROOT + "/loans"), Map.of("copyId", free.id(), "studentId", student), admin).andExpect(status().isInternalServerError());
            call(put(ROOT + "/loans/" + loan.id() + "/return"), Map.of("expectedVersion", 0), admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(snapshot()).isEqualTo(before);
        } finally { jdbc.execute("DROP TRIGGER IF EXISTS reject_library_audit_test ON library_audit_events"); }
    }
    @Test void competingBorrowAndReturnOperationsCommitOneAndPreserveAuditHistory() throws Exception {
        long before = auditCount();
        var outcomes = race(() -> service.borrow(actor, copy.id(), student), () -> service.borrow(actor, copy.id(), otherStudent));
        assertOneError(outcomes, LibraryService.CopyAlreadyLoanedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_loans WHERE copy_id=? AND status='OPEN'", Long.class, copy.id())).isEqualTo(1L);
        UUID id = jdbc.queryForObject("SELECT id FROM library_loans WHERE copy_id=?", UUID.class, copy.id());
        assertOneError(race(() -> service.returnBook(actor, id, 0), () -> service.returnBook(actor, id, 0)), LibraryService.StaleVersionException.class);
        assertThat(service.loan(id).rowVersion()).isEqualTo(1); assertThat(auditCount()).isEqualTo(before + 2);
        assertThat(service.borrow(actor, copy.id(), student).id()).isNotEqualTo(id);
    }
    @Test void cachedReferencesAndLoansAreRefreshedBeforeAdmissionOrStaleUpdates() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(service.title(title.id()).status()).isEqualTo(LibraryStatus.ACTIVE);
                run(pool, () -> service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.INACTIVE, 0));
                assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class); tx.setRollbackOnly();
            });
            service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.ACTIVE, 1);
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(service.copy(copy.id()).status()).isEqualTo(LibraryStatus.ACTIVE);
                run(pool, () -> service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.INACTIVE, 0));
                assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class); tx.setRollbackOnly();
            });
            service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.ACTIVE, 1);
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(students.get(student).status()).isEqualTo(StudentStatus.ACTIVE);
                run(pool, () -> jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?", student));
                assertThatThrownBy(() -> service.borrow(actor, copy.id(), student)).isInstanceOf(LibraryService.UnavailableReferenceException.class); tx.setRollbackOnly();
            });
            jdbc.update("UPDATE students SET status='ACTIVE' WHERE id=?", student);
            var loan = service.borrow(actor, copy.id(), student);
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(service.loan(loan.id()).rowVersion()).isZero(); run(pool, () -> service.returnBook(actor, loan.id(), 0));
                assertThatThrownBy(() -> service.returnBook(actor, loan.id(), 0)).isInstanceOf(LibraryService.StaleVersionException.class); tx.setRollbackOnly();
            });
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void admissionSerializesWithTitleCopyDeactivationAndReturnWithoutDuplicateOpenLoans() throws Exception {
        var titleRace = race(() -> service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.INACTIVE, 0),
                () -> service.borrow(actor, copy.id(), student));
        assertThat(titleRace.get(0)).isNull();
        if (titleRace.get(1) != null) assertThat(titleRace.get(1)).isInstanceOf(LibraryService.UnavailableReferenceException.class);
        assertThat(service.title(title.id()).status()).isEqualTo(LibraryStatus.INACTIVE);
        var current = jdbc.queryForList("SELECT id FROM library_loans WHERE copy_id=? AND status='OPEN'", UUID.class, copy.id());
        assertThat(current.size()).isLessThanOrEqualTo(1);
        if (!current.isEmpty()) service.returnBook(actor, current.get(0), 0);
        service.updateTitle(actor, title.id(), title.code(), title.title(), title.author(), LibraryStatus.ACTIVE, 1);
        var copyRace = race(() -> service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.INACTIVE, 0),
                () -> service.borrow(actor, copy.id(), student));
        assertThat(copyRace.get(0)).isNull();
        if (copyRace.get(1) != null) assertThat(copyRace.get(1)).isInstanceOf(LibraryService.UnavailableReferenceException.class);
        assertThat(service.copy(copy.id()).status()).isEqualTo(LibraryStatus.INACTIVE);
        current = jdbc.queryForList("SELECT id FROM library_loans WHERE copy_id=? AND status='OPEN'", UUID.class, copy.id());
        assertThat(current.size()).isLessThanOrEqualTo(1);
        if (!current.isEmpty()) service.returnBook(actor, current.get(0), 0);
        service.updateCopy(actor, copy.id(), copy.code(), LibraryStatus.ACTIVE, 1);
        var old = service.borrow(actor, copy.id(), student);
        var returnRace = race(() -> service.returnBook(actor, old.id(), 0), () -> service.borrow(actor, copy.id(), otherStudent));
        assertThat(returnRace.get(0)).isNull();
        if (returnRace.get(1) != null) assertThat(returnRace.get(1)).isInstanceOf(LibraryService.CopyAlreadyLoanedException.class);
        assertThat(service.loan(old.id()).status()).isEqualTo(BookLoan.Status.RETURNED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_loans WHERE copy_id=? AND status='OPEN'", Long.class, copy.id())).isLessThanOrEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM library_audit_events WHERE target_id=? AND action='RETURNED'", Long.class, old.id())).isEqualTo(1L);
    }
    @Test void eachProductionLockUsesSeparateTransactionsBounded55P03AndRecoversAfterRelease() throws Exception {
        var pool = Executors.newSingleThreadExecutor(); var loan = service.borrow(actor, copy.id(), student);
        try {
            for (Runnable held : List.<Runnable>of(() -> repository.lockTitle(title.id()), () -> repository.lockCopy(copy.id()), () -> repository.lockLoan(loan.id()))) {
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    held.run();
                    try {
                        var failure = pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(other -> {
                            jdbc.execute("SET LOCAL lock_timeout='500ms'"); service.returnBook(actor, loan.id(), 0);
                        }))).get(10, TimeUnit.SECONDS);
                        assertThat(failure).isNotNull(); boolean found = false;
                        for (Throwable cause = failure; cause != null; cause = cause.getCause()) if (cause instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) found = true;
                        assertThat(found).isTrue();
                    } catch (Exception failure) { throw new AssertionError(failure); }
                });
            }
            assertThat(service.returnBook(actor, loan.id(), 0).rowVersion()).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void boundsFiltersLiteralSearchSortsAndActualUuidTiePagesAcrossAllResources() throws Exception {
        var first = service.createTitle(actor, prefix + "-AA", prefix + "%_! title", "Author");
        var second = service.createTitle(actor, prefix + "-BB", prefix + "%_! title", "Author");
        var ids = jdbc.queryForList("SELECT id FROM library_titles WHERE id IN (?,?) ORDER BY id", UUID.class, first.id(), second.id());
        for (int page = 0; page < 2; page++) call(get(ROOT + "/titles").param("q", prefix + "%_!").param("sort", "title,asc").param("page", String.valueOf(page)).param("size", "1"), null, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()));
        var extra = service.createCopy(actor, title.id(), prefix + "-%_!");
        call(get(ROOT + "/copies").param("q", "%_!").param("titleId", title.id().toString()), null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE library_copies SET created_at=TIMESTAMPTZ '2026-01-01 00:00:00Z' WHERE title_id=?", title.id());
        ids = jdbc.queryForList("SELECT id FROM library_copies WHERE title_id=? ORDER BY id", UUID.class, title.id());
        for (int page = 0; page < 2; page++) call(get(ROOT + "/copies").param("titleId", title.id().toString()).param("sort", "createdAt,asc").param("page", String.valueOf(page)).param("size", "1"), null, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()));
        var one = service.borrow(actor, copy.id(), student); var two = service.borrow(actor, extra.id(), student);
        jdbc.update("UPDATE library_loans SET borrowed_at=TIMESTAMPTZ '2026-01-01 00:00:00Z' WHERE student_id=?", student);
        ids = jdbc.queryForList("SELECT id FROM library_loans WHERE student_id=? ORDER BY id", UUID.class, student);
        for (int page = 0; page < 2; page++) call(get(ROOT + "/loans").param("studentId", student.toString()).param("sort", "borrowedAt,asc").param("page", String.valueOf(page)).param("size", "1"), null, admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()));
        call(get(ROOT + "/loans").param("copyId", copy.id().toString()).param("status", "OPEN"), null, admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String resource : List.of("titles", "copies", "loans")) {
            for (var params : List.of(Map.of("page", "-1"), Map.of("size", "101"), Map.of("page", "2147483647", "size", "100"), Map.of("status", "UNKNOWN"), Map.of("sort", "id,bad"))) {
                var request = get(ROOT + "/" + resource); params.forEach(request::param);
                call(request, null, admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
            }
            var fields = resource.equals("titles") ? List.of("code", "title", "author", "status", "createdAt", "updatedAt")
                    : resource.equals("copies") ? List.of("code", "status", "createdAt", "updatedAt") : List.of("borrowedAt", "dueAt", "returnedAt", "status", "createdAt", "updatedAt");
            for (String field : fields) for (String direction : List.of("asc", "desc")) call(get(ROOT + "/" + resource).param("sort", field + "," + direction), null, admin).andExpect(status().isOk());
        }
        assertThat(service.loan(one.id()).copyId()).isEqualTo(copy.id()); assertThat(service.loan(two.id()).copyId()).isEqualTo(extra.id());
    }
    private UserAccount account(RoleCode role) { return accounts.save(UserAccount.create(UUID.randomUUID(), UUID.randomUUID() + "@campus.example", "Library User", passwords.encode("test-only-placeholder"), AccountStatus.ACTIVE, Set.of(roles.findByCode(role).orElseThrow()), Instant.now())); }
    private Map<String, Object> titleUpdate(LibraryStatus status, long version) { return new LinkedHashMap<>(Map.of("code", title.code(), "title", title.title(), "author", title.author(), "status", status, "expectedVersion", version)); }
    private Map<String, Object> copyUpdate(LibraryStatus status, long version) { return new LinkedHashMap<>(Map.of("code", copy.code(), "status", status, "expectedVersion", version)); }
    private Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("library_titles", "library_copies", "library_loans", "library_audit_events")) result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")); return result;
    }
    private long auditCount() { return jdbc.queryForObject("SELECT count(*) FROM library_audit_events", Long.class); }
    private ResultActions call(MockHttpServletRequestBuilder request, Object body, String token) throws Exception {
        if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType("application/json").content(json.writeValueAsString(body)); return mvc.perform(request);
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void run(ExecutorService pool, Runnable action) {
        try { pool.submit(action).get(10, TimeUnit.SECONDS); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void assertOneError(List<Throwable> outcomes, Class<? extends Throwable> type) {
        assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(type);
    }
    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Throwable>>();
            for (var action : List.of(first, second)) futures.add(pool.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); return catchThrowable(action::run);
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            return Arrays.asList(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { go.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
}
