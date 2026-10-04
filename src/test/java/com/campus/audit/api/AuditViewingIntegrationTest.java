package com.campus.audit.api;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.audit.application.AuditViewingService;
import com.campus.shared.application.audit.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.library.application.LibraryService;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class AuditViewingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditViewingService service;
    @Autowired List<AuditReadPort> ports;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired LibraryService library;
    @MockitoSpyBean NamedParameterJdbcTemplate namedJdbc;
    static final String ROOT="/api/v1/admin/audits";
    static final Map<AuditSource,String> TABLES=Map.of(AuditSource.IDENTITY,"identity_admin_audit_events",AuditSource.PEOPLE,"people_registry_audit_events",
            AuditSource.ACADEMIC,"academic_audit_events",AuditSource.DORMITORY,"dormitory_audit_events",AuditSource.FINANCE,"finance_audit_events",
            AuditSource.NOTIFICATION,"notification_audit_events",AuditSource.EVENT,"event_audit_events",AuditSource.LIBRARY,"library_audit_events");
    static final Map<AuditSource,String> RESOURCES=Map.of(AuditSource.IDENTITY,"USER",AuditSource.PEOPLE,"STUDENT",AuditSource.ACADEMIC,"PROGRAM",
            AuditSource.DORMITORY,"BUILDING",AuditSource.FINANCE,"FEE",AuditSource.NOTIFICATION,"TEMPLATE",AuditSource.EVENT,"EVENT",AuditSource.LIBRARY,"TITLE");
    final Instant time=Instant.parse("2026-01-01T00:00:00Z");
    UUID actor; String admin,user; Map<AuditSource,UUID> ids,targets;
    @BeforeEach void setup() {
        var account=account(RoleCode.ADMIN); actor=account.id(); admin=tokens.accessToken(account); user=tokens.accessToken(account(RoleCode.USER));
        ids=new EnumMap<>(AuditSource.class); targets=new EnumMap<>(AuditSource.class);
        for(var source:AuditSource.values()) { UUID target=source==AuditSource.IDENTITY?actor:UUID.randomUUID(); targets.put(source,target); ids.put(source,insert(source,target,time)); }
    }
    @Test void everySourceGetAndListRequiresAdminAndDeclaresBearer() throws Exception {
        for(var source:AuditSource.values()) for(String path:List.of(ROOT+"/"+source,ROOT+"/"+source+"/"+ids.get(source))) {
            call(get(path),"").andExpect(status().isUnauthorized()); call(get(path),user).andExpect(status().isForbidden());
        }
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String path:List.of(ROOT+"/{source}",ROOT+"/{source}/{id}")) {
            assertThat(spec.path("paths").path(path).has("get")).isTrue();
            assertThat(spec.path("paths").path(path).path("get").path("security").get(0).has("bearerAuth")).isTrue();
            assertThat(spec.path("paths").path(path).size()).isEqualTo(1);
        }
        assertThat(spec.path("components").path("schemas").has("AuditViewingPage")).isTrue();
    }
    @Test void readsExactRecordedFieldsAcrossEveryOwnerWithoutInventedVersionsOrRawMetadata() throws Exception {
        for(var source:AuditSource.values()) {
            var result=read(call(get(ROOT+"/"+source+"/"+ids.get(source)),admin).andExpect(status().isOk()));
            assertThat(result.path("id").asText()).isEqualTo(ids.get(source).toString()); assertThat(result.path("source").asText()).isEqualTo(source.name());
            assertThat(result.path("targetId").asText()).isEqualTo(targets.get(source).toString()); assertThat(result.path("actorId").asText()).isEqualTo(actor.toString());
            assertThat(result.path("resource").asText()).isEqualTo(RESOURCES.get(source)); assertThat(Instant.parse(result.path("occurredAt").asText())).isEqualTo(time);
            if(historical(source)) { assertThat(result.path("resourceVersion").isNull()).isTrue(); assertThat(result.path("metadata").size()).isZero(); }
            else { assertThat(result.path("resourceVersion").asLong()).isEqualTo(7); assertThat(result.path("metadata").toString()).isEqualTo(source==AuditSource.EVENT?"{\"status\":\"DRAFT\"}":"{\"status\":\"ACTIVE\"}"); }
            call(get(ROOT+"/"+source).param("targetId",targets.get(source).toString()).param("actorId",actor.toString()).param("resource",RESOURCES.get(source)).param("action",action(source)),admin)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(ids.get(source).toString()));
            call(get(ROOT+"/"+source+"/"+UUID.randomUUID()),admin).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"));
        }
        var created=library.createTitle(actor,"Q"+UUID.randomUUID().toString().substring(0,12),"Audit-linked title","Author");
        UUID event=jdbc.queryForObject("SELECT id FROM library_audit_events WHERE target_id=?",UUID.class,created.id());
        assertThat(service.get(AuditSource.LIBRARY,event)).extracting(AuditView::targetId,AuditView::actorId,AuditView::action,AuditView::resourceVersion)
                .containsExactly(created.id(),actor,"CREATED",0L);
    }
    @Test void legacyMalformedTextAndJsonPayloadsAreSafelyProjectedWithoutBreakingPages() throws Exception {
        for(var source:List.of(AuditSource.IDENTITY,AuditSource.PEOPLE)) {
            for(String metadata:List.of("plain historic text","{malformed","[]","{\"password\":\"private-fixture\",\"status\":\"ACTIVE\"}")) {
                jdbc.update("UPDATE "+TABLES.get(source)+" SET metadata=? WHERE id=?",metadata,ids.get(source));
                assertThat(service.get(source,ids.get(source)).metadata()).isEmpty();
            }
        }
        for(var source:AuditSource.values()) if(!historical(source)) {
            jdbc.update("UPDATE "+TABLES.get(source)+" SET metadata=jsonb_build_object('status',jsonb_build_object('nested','private-fixture'),'token','private-fixture','body',?) WHERE id=?","x".repeat(20000),ids.get(source));
            assertThat(service.get(source,ids.get(source)).metadata()).isEmpty();
            jdbc.update("UPDATE "+TABLES.get(source)+" SET metadata=jsonb_build_object('status','private-fixture') WHERE id=?",ids.get(source));
            assertThat(service.get(source,ids.get(source)).metadata()).isEmpty();
            jdbc.update("UPDATE "+TABLES.get(source)+" SET metadata=jsonb_build_object('status',NULL) WHERE id=?",ids.get(source));
            call(get(ROOT+"/"+source).param("targetId",targets.get(source).toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].metadata").isEmpty());
        }
    }
    @Test void exactTimeBoundariesAndRealEqualTimestampPagesWorkForEverySource() throws Exception {
        for(var source:AuditSource.values()) {
            var target=targets.get(source); insert(source,target,time); insert(source,target,time.plusSeconds(1));
            var expected=jdbc.queryForList("SELECT id FROM "+TABLES.get(source)+" WHERE "+targetColumn(source)+"=? AND occurred_at=? ORDER BY id",UUID.class,target,java.sql.Timestamp.from(time));
            for(String direction:List.of("asc","desc")) for(int page=0;page<2;page++)
                call(get(ROOT+"/"+source).param("targetId",target.toString()).param("from",time.toString()).param("until",time.plusSeconds(1).toString())
                        .param("sort","occurredAt,"+direction).param("size","1").param("page",String.valueOf(page)),admin)
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                        .andExpect(jsonPath("$.content[0].id").value(expected.get(page).toString()));
            call(get(ROOT+"/"+source).param("targetId",target.toString()).param("from",time.plusSeconds(1).toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            call(get(ROOT+"/"+source).param("targetId",target.toString()).param("until",time.toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            call(get(ROOT+"/"+source).param("targetId",UUID.randomUUID().toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
    }
    @Test void rejectsInvalidBoundsTypesRangesSortsAndOwnerSpecificFilters() throws Exception {
        for(var source:AuditSource.values()) {
            for(var invalid:List.of(Map.of("page","-1"),Map.of("size","101"),Map.of("size","0"),Map.of("page","2147483647","size","100"),
                    Map.of("resource","NOT_SUPPORTED"),Map.of("action","CREATED' OR 1=1"),Map.of("actorId","not-a-uuid"),Map.of("from","not-a-time"),
                    Map.of("from",time.toString(),"until",time.toString()),Map.of("from",time.toString(),"until",time.minusSeconds(1).toString()),Map.of("sort","id,asc"),Map.of("sort","occurredAt,bad"))) {
                var request=get(ROOT+"/"+source); invalid.forEach(request::param);
                call(request,admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
            }
            int maximum=source==AuditSource.IDENTITY?64:source==AuditSource.PEOPLE||source==AuditSource.ACADEMIC?32:16;
            call(get(ROOT+"/"+source).param("action","X".repeat(maximum+1)),admin).andExpect(status().isBadRequest());
            call(get(ROOT+"/"+source).param("action","UNKNOWN"),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        call(get(ROOT+"/UNKNOWN"),admin).andExpect(status().isBadRequest()); call(get(ROOT+"/IDENTITY/not-a-uuid"),admin).andExpect(status().isBadRequest());
        call(get(ROOT+"/LIBRARY/"+ids.get(AuditSource.EVENT)),admin).andExpect(status().isNotFound());
    }
    @Test void allQueryPortsAndHttpReadsLeaveEveryAuditRowAndFlywayHistoryUnchanged() throws Exception {
        var before=snapshot();
        for(var port:ports) {
            var source=port.source(); assertThat(port.find(ids.get(source))).isPresent();
            assertThat(port.search(new AuditSearch(0,100,targets.get(source),actor,RESOURCES.get(source),action(source),time,time.plusSeconds(1),true)).totalElements()).isEqualTo(1);
            call(get(ROOT+"/"+source),admin).andExpect(status().isOk()); call(get(ROOT+"/"+source+"/"+ids.get(source)),admin).andExpect(status().isOk());
        }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void countAndPageShareOneDatabaseSnapshotDespiteConcurrentCommittedAuditInsert() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            for(var source:AuditSource.values()) {
                var counted=new CountDownLatch(1); var resume=new CountDownLatch(1);
                doAnswer(invocation->{
                    Object result=invocation.callRealMethod();
                    String sql=invocation.getArgument(0); Map<String,?> arguments=invocation.getArgument(1);
                    if(sql.startsWith("SELECT count(*) FROM "+TABLES.get(source)+" ") && targets.get(source).equals(arguments.get("target"))) {
                        counted.countDown(); if(!resume.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Snapshot test timeout");
                    }
                    return result;
                }).when(namedJdbc).queryForObject(anyString(),anyMap(),eq(Long.class));
                try {
                    var result=pool.submit(()->service.search(source,new AuditSearch(0,100,targets.get(source),actor,null,null,null,null,true)));
                    assertThat(counted.await(10,TimeUnit.SECONDS)).isTrue();
                    UUID appended=insert(source,targets.get(source),time.plusSeconds(1)); resume.countDown();
                    var snapshot=result.get(15,TimeUnit.SECONDS);
                    assertThat(snapshot.totalElements()).isEqualTo(1); assertThat(snapshot.content()).singleElement().satisfies(event->assertThat(event.id()).isEqualTo(ids.get(source)));
                    var latest=service.search(source,new AuditSearch(0,100,targets.get(source),actor,null,null,null,null,true));
                    assertThat(latest.totalElements()).isEqualTo(2); assertThat(latest.content().stream().map(AuditView::id).toList()).contains(appended);
                } finally { resume.countDown(); reset(namedJdbc); }
            }
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private UUID insert(AuditSource source,UUID target,Instant occurred) {
        UUID id=UUID.randomUUID();
        String metadata="{\"status\":\""+(source==AuditSource.EVENT?"DRAFT":"ACTIVE")+"\",\"password\":\"private-fixture\",\"nested\":{\"token\":\"private-fixture\"}}";
        if(source==AuditSource.IDENTITY) jdbc.update("INSERT INTO identity_admin_audit_events(id,actor_user_id,target_user_id,action,occurred_at,metadata) VALUES (?,?,?,?,?,?)",id,actor,target,action(source),java.sql.Timestamp.from(occurred),metadata);
        else if(source==AuditSource.PEOPLE) jdbc.update("INSERT INTO people_registry_audit_events(id,actor_user_id,resource_type,target_id,action,occurred_at,metadata) VALUES (?,?,?,?,?,?,?)",id,actor,RESOURCES.get(source),target,action(source),java.sql.Timestamp.from(occurred),metadata);
        else jdbc.update("INSERT INTO "+TABLES.get(source)+"(id,actor_user_id,resource_type,target_id,action,resource_version,occurred_at,metadata) VALUES (?,?,?,?,?,7,?,?::jsonb)",id,actor,RESOURCES.get(source),target,action(source),java.sql.Timestamp.from(occurred),metadata);
        return id;
    }
    private boolean historical(AuditSource source) { return source==AuditSource.IDENTITY||source==AuditSource.PEOPLE; }
    private String action(AuditSource source) { return source==AuditSource.IDENTITY?"USER_CREATED":"CREATED"; }
    private String targetColumn(AuditSource source) { return source==AuditSource.IDENTITY?"target_user_id":"target_id"; }
    private UserAccount account(RoleCode role) { return accounts.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Audit User",passwords.encode("test-only-placeholder"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private ResultActions call(MockHttpServletRequestBuilder request,String token) throws Exception { if(!token.isEmpty()) request.header("Authorization","Bearer "+token); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private Map<String,List<Map<String,Object>>> snapshot() {
        var result=new TreeMap<String,List<Map<String,Object>>>(); for(String table:TABLES.values()) result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"));
        result.put("flyway_schema_history",jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")); return result;
    }
}
