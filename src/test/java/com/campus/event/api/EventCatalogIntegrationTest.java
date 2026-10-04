package com.campus.event.api;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.event.application.*;
import com.campus.event.domain.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
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
class EventCatalogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EventCatalogService service;
    @Autowired EventRepository repository;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    static final String ADMIN="/api/v1/admin/events", PUBLIC="/api/v1/events";
    final Instant start=Instant.parse("2026-12-01T08:00:00Z"), end=start.plusSeconds(7200);
    UUID actor; String admin,user,prefix; CampusEvent event;
    @BeforeEach void setup() {
        prefix=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        var account=account(RoleCode.ADMIN); actor=account.id(); admin=tokens.accessToken(account);
        user=tokens.accessToken(account(RoleCode.USER));
        event=service.create(actor,prefix+"-EV",prefix+" event","Campus event description",start,end,3);
    }
    @Test void enforcesAllCatalogOperationRolesAndDeclaresBearerSchemas() throws Exception {
        for (var request : List.of(post(ADMIN),get(ADMIN),get(ADMIN+"/"+event.id()),put(ADMIN+"/"+event.id())))
            call(request,null,"").andExpect(status().isUnauthorized());
        for (var request : List.of(post(ADMIN),get(ADMIN),get(ADMIN+"/"+event.id()),put(ADMIN+"/"+event.id())))
            call(request,null,user).andExpect(status().isForbidden());
        for (var request : List.of(get(PUBLIC),get(PUBLIC+"/"+event.id())))
            call(request,null,"").andExpect(status().isUnauthorized());
        call(get(PUBLIC+"/"+event.id()),null,user).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(event.id().toString()));
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for (String path:List.of(ADMIN,ADMIN+"/{id}",PUBLIC,PUBLIC+"/{id}"))
            spec.path("paths").path(path).elements().forEachRemaining(operation -> assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue());
        assertThat(spec.path("components").path("schemas").path("CampusEventUpdate").path("required").toString()).contains("capacity","expectedVersion","startsAt","endsAt");
    }
    @Test void createsUpdatesAndRetainsStoredPrecisionTrustedActorAndTerminalHistory() throws Exception {
        var body=create(prefix+"-NEW"); body.put("title","😀".repeat(160)); body.put("description","😀".repeat(4000));
        body.put("actor",UUID.randomUUID().toString());
        var created=read(call(post(ADMIN),body,admin).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        assertThat(created).isEqualTo(read(call(get(ADMIN+"/"+created.path("id").asText()),null,admin).andExpect(status().isOk())));
        var changed=read(call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.OPEN,0),admin).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1)));
        assertThat(changed).isEqualTo(read(call(get(PUBLIC+"/"+event.id()),null,user).andExpect(status().isOk())));
        assertThat(changed.path("createdAt").asText()).isEqualTo(json.valueToTree(event).path("createdAt").asText());
        var audit=jdbc.queryForMap("SELECT actor_user_id,resource_version,metadata FROM event_audit_events WHERE target_id=? AND action='UPDATED'",event.id());
        assertThat(audit.get("actor_user_id")).isEqualTo(actor); assertThat(audit.get("resource_version")).isEqualTo(1L);
        call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.OPEN,0),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.DRAFT,1),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_EVENT_STATE"));
        call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.CLOSED,1),admin).andExpect(status().isOk());
        call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.OPEN,2),admin).andExpect(status().isConflict());
    }
    @Test void rejectsInvalidBodiesDuplicateCodesMissingAndStaleUpdatesWithoutWrites() throws Exception {
        var before=rows(); var count=auditCount();
        for (var change:List.of(Map.of("title","x"),Map.of("title","😀".repeat(161)),Map.of("description","x".repeat(4001)),
                Map.of("code","ß".repeat(17)),Map.of("code"," \t\n\r\u000b\f"),Map.of("capacity",0),Map.of("capacity",1.5),
                Map.of("capacity","3"),Map.of("capacity",2147483648L),
                Map.of("endsAt",start.toString()),Map.of("startsAt","invalid-date"))) {
            var body=create(prefix+"-BAD"); body.putAll(change);
            call(post(ADMIN),body,admin).andExpect(status().isBadRequest());
        }
        var missing=create(prefix+"-BAD"); missing.remove("capacity"); call(post(ADMIN),missing,admin).andExpect(status().isBadRequest());
        call(post(ADMIN),create(event.code().toLowerCase(Locale.ROOT)),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_CODE_ALREADY_EXISTS"));
        var invalid=update(CampusEvent.Status.OPEN,-1); call(put(ADMIN+"/"+event.id()),invalid,admin).andExpect(status().isBadRequest());
        call(get(ADMIN+"/"+UUID.randomUUID()),null,admin).andExpect(status().isNotFound());
        call(get(PUBLIC+"/not-a-uuid"),null,user).andExpect(status().isBadRequest());
        assertThat(rows()).isEqualTo(before); assertThat(auditCount()).isEqualTo(count);
    }
    @Test void rollsBackBothCatalogMutationsWhenAuditInsertFails() throws Exception {
        var before=rows(); var count=auditCount();
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_event_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_event_audit_test BEFORE INSERT ON event_audit_events FOR EACH ROW EXECUTE FUNCTION reject_event_audit_test()");
        try {
            call(post(ADMIN),create(prefix+"-ROLLBACK"),admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            call(put(ADMIN+"/"+event.id()),update(CampusEvent.Status.OPEN,0),admin).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(rows()).isEqualTo(before); assertThat(auditCount()).isEqualTo(count);
        } finally { jdbc.execute("DROP TRIGGER IF EXISTS reject_event_audit_test ON event_audit_events"); }
    }
    @Test void rejectsCoercedFractionalStringAndOverflowVersionsWithoutWriting() throws Exception {
        var before=rows(); var count=auditCount();
        for (Object invalid:List.of(0.5,"0",1.0,new java.math.BigInteger("9223372036854775808"),false)) {
            var body=update(CampusEvent.Status.OPEN,0); body.put("expectedVersion",invalid);
            call(put(ADMIN+"/"+event.id()),body,admin).andExpect(status().isBadRequest());
        }
        assertThat(rows()).isEqualTo(before); assertThat(auditCount()).isEqualTo(count);
    }
    @Test void filtersLiteralSearchAndAllSortsWithActualTiedUuidPages() throws Exception {
        var first=service.create(actor,prefix+"-QA",prefix+"%_!😀 event","Description",start,end,2);
        var second=service.create(actor,prefix+"-QB",prefix+"%_!😀 event","Description",start,end,2);
        var ids=jdbc.queryForList("SELECT id FROM campus_events WHERE id IN (?,?) ORDER BY id",UUID.class,first.id(),second.id());
        for (String path:List.of(ADMIN,PUBLIC)) {
            String token=path.equals(ADMIN)?admin:user;
            for (int page=0;page<2;page++) call(get(path).param("q",prefix+"%_!😀").param("sort","startsAt,asc").param("size","1").param("page",String.valueOf(page)),null,token)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()));
            for (String field:List.of("code","title","startsAt","endsAt","capacity","status","createdAt","updatedAt"))
                for (String direction:List.of("asc","desc")) call(get(path).param("q",prefix).param("sort",field+","+direction).param("status","DRAFT"),null,token).andExpect(status().isOk());
            for (var params:List.of(Map.of("page","-1"),Map.of("size","101"),Map.of("page","2147483647","size","100"),
                    Map.of("sort","description,asc"),Map.of("sort","code,bad"),Map.of("status","UNKNOWN"),Map.of("q","x".repeat(101)))) {
                var request=get(path); params.forEach(request::param);
                call(request,null,token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
            }
        }
    }
    @Test void concurrentCatalogEditsCommitOneVersionAndEvent() throws Exception {
        var worker=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var go=new CountDownLatch(1); long count=auditCount();
        try {
            var results=new ArrayList<Future<Throwable>>();
            for (int i=0;i<2;i++) results.add(worker.submit(() -> { ready.countDown(); if(!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); return catchThrowable(() -> open(0)); }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            var outcomes=Arrays.asList(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(EventCatalogService.StaleVersionException.class);
            assertThat(service.get(event.id()).rowVersion()).isEqualTo(1); assertThat(auditCount()).isEqualTo(count+1);
        } finally { go.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void refreshesCachedEventAndTimesOutProductionLockThenSucceedsAfterRelease() throws Exception {
        var worker=Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                assertThat(service.get(event.id()).rowVersion()).isZero();
                try { worker.submit(() -> open(0)).get(10,TimeUnit.SECONDS); } catch(Exception failure) { throw new AssertionError(failure); }
                assertThatThrownBy(() -> open(0)).isInstanceOf(EventCatalogService.StaleVersionException.class);
                status.setRollbackOnly();
            });
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                repository.lock(event.id());
                try {
                    var error=worker.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        jdbc.execute("SET LOCAL lock_timeout='500ms'"); open(1);
                    }))).get(10,TimeUnit.SECONDS);
                    assertThat(error).isNotNull(); boolean found=false;
                    for (Throwable cause=error;cause!=null;cause=cause.getCause())
                        if (cause instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) found=true;
                    assertThat(found).isTrue();
                } catch(Exception failure) { throw new AssertionError(failure); }
            });
            assertThat(open(1).rowVersion()).isEqualTo(2);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private CampusEvent open(long version) { return service.update(actor,event.id(),event.code(),event.title(),event.description(),start,end,3,CampusEvent.Status.OPEN,version); }
    private Map<String,Object> create(String code) { var body=new LinkedHashMap<String,Object>(); body.put("code",code); body.put("title",prefix+" event"); body.put("description","Campus event description"); body.put("startsAt",start); body.put("endsAt",end); body.put("capacity",3); return body; }
    private Map<String,Object> update(CampusEvent.Status status,long version) { var body=create(event.code()); body.put("status",status); body.put("expectedVersion",version); return body; }
    private UserAccount account(RoleCode role) { return users.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Event test user",passwords.encode("test-only-placeholder"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private List<Map<String,Object>> rows() { return jdbc.queryForList("SELECT * FROM campus_events ORDER BY id"); }
    private long auditCount() { return jdbc.queryForObject("SELECT count(*) FROM event_audit_events",Long.class); }
    private ResultActions call(MockHttpServletRequestBuilder request,Object body,String token) throws Exception { if(!token.isEmpty()) request.header("Authorization","Bearer "+token); if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body)); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
}
