package com.campus.finance.api;

import com.campus.testsupport.PostgresApplicationTest;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import com.campus.finance.application.*;
import com.campus.finance.domain.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
class FinanceObligationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceObligationService service;
    @Autowired FinanceRepository repository;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService units;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired PlatformTransactionManager transactions;
    UUID actor,unit,student,fee;
    String admin,user,prefix;
    static final String ROOT = "/api/v1/admin/finance";
    static final LocalDate DUE = LocalDate.of(2026,1,1);
    @BeforeEach void setup() {
        prefix = UUID.randomUUID().toString().substring(0,8);
        var account = account(RoleCode.ADMIN); actor = account.id(); admin = tokens.accessToken(account); user = tokens.accessToken(account(RoleCode.USER));
        unit = units.create(prefix,"Unit",OrganizationUnitType.FACULTY,OrganizationUnitStatus.ACTIVE).id();
        student = students.create(prefix,"Student",null,null,unit,StudentStatus.ACTIVE).id();
        fee = service.createFee(actor,prefix,"Fee",new BigDecimal("100000")).id();
    }
    @Test void protectsAllEightOperationsAndValidationWritesNoAudit() throws Exception {
        long before = events(); var original = rows();
        for (String bearer : List.of("",user)) for (String resource : List.of("fees","charges")) {
            for (var request : List.of(get(ROOT+"/"+resource),get(ROOT+"/"+resource+"/"+UUID.randomUUID()),post(ROOT+"/"+resource),put(ROOT+"/"+resource+"/"+UUID.randomUUID()))) {
                if (!bearer.isEmpty()) request.header("Authorization","Bearer "+bearer);
                mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().is(bearer.isEmpty()?401:403));
            }
        }
        for (String resource : List.of("fees","charges")) {
            call(post(ROOT+"/"+resource),Map.of()).andExpect(status().isBadRequest());
            call(put(ROOT+"/"+resource+"/"+fee),Map.of()).andExpect(status().isBadRequest());
            call(get(ROOT+"/"+resource+"/bad-id"),null).andExpect(status().isBadRequest());
            call(get(ROOT+"/"+resource+"/"+UUID.randomUUID()),null).andExpect(status().isNotFound());
            for (var entry : List.of(Map.entry("page","-1"),Map.entry("page","2147483647"),Map.entry("size","0"),Map.entry("size","101"),
                    Map.entry("status","UNKNOWN"),Map.entry("q","x".repeat(101)),Map.entry("sort","id,asc"),Map.entry("sort","amount,wrong")))
                call(get(ROOT+"/"+resource).param(entry.getKey(),entry.getValue()),null).andExpect(status().isBadRequest());
        }
        assertThat(events()).isEqualTo(before); assertThat(rows()).isEqualTo(original);
    }
    @Test void acceptsExactAmountBoundariesAndRejectsFractionalOrOverflowRequests() throws Exception {
        for (String amount : List.of("0","-1","1.001","0.001","10000000000000000000"))
            call(post(ROOT+"/fees"),Map.of("code",prefix+amount,"name","Fee","amount",new BigDecimal(amount))).andExpect(status().isBadRequest());
        var one = read(call(post(ROOT+"/fees"),Map.of("code",prefix+"ONE","name","😀".repeat(160),"amount",new BigDecimal("1.00"))).andExpect(status().isCreated()));
        assertThat(one.get("amount").decimalValue()).isEqualByComparingTo(BigDecimal.ONE);
        var max = read(call(post(ROOT+"/fees"),Map.of("code",prefix+"MAX","name","Maximum","amount",FinanceValues.MAX_AMOUNT)).andExpect(status().isCreated()));
        assertThat(max.get("amount").decimalValue()).isEqualByComparingTo(FinanceValues.MAX_AMOUNT);
        var stored = service.fee(UUID.fromString(max.get("id").asText())); assertThat(stored.amount()).isEqualTo(FinanceValues.MAX_AMOUNT);
        var charge = service.createCharge(actor,prefix+"MAXCH",student,stored.id(),DUE);
        assertThat(service.charge(charge.id()).amount()).isEqualTo(FinanceValues.MAX_AMOUNT);
    }
    @Test void snapshotsFinancialTermsAndPreservesHistoryThroughFeeAndStudentDeactivation() throws Exception {
        var body = new LinkedHashMap<String,Object>(chargeBody(prefix+"CH"));
        body.put("amount",1); body.put("actorUserId",UUID.randomUUID());
        var charge = read(call(post(ROOT+"/charges"),body).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        UUID id = UUID.fromString(charge.get("id").asText());
        assertThat(read(call(get(ROOT+"/charges/"+id),null).andExpect(status().isOk()))).isEqualTo(charge);
        var old = service.fee(fee);
        service.updateFee(actor,fee,"NEW"+prefix,"New Fee",BigDecimal.ONE,FeeStatus.INACTIVE,old.rowVersion());
        assertThat(service.charge(id).amount()).isEqualTo(new BigDecimal("100000"));
        assertThat(service.charge(id).feeName()).isEqualTo("Fee"); assertThat(service.charge(id).feeCode()).isEqualTo(prefix.toUpperCase(Locale.ROOT));
        var profile = students.get(student);
        students.update(student,profile.studentNumber(),profile.fullName(),profile.email(),profile.identityUserId(),unit,StudentStatus.INACTIVE,profile.rowVersion());
        var mutation = new LinkedHashMap<String,Object>(Map.of("status","CANCELLED","expectedVersion",0));
        mutation.put("amount",1); mutation.put("studentId",UUID.randomUUID()); mutation.put("feeId",UUID.randomUUID());
        var cancelled = read(call(put(ROOT+"/charges/"+id),mutation).andExpect(status().isOk()));
        assertThat(cancelled.get("rowVersion").asLong()).isEqualTo(1);
        for (String field : List.of("id","chargeNumber","studentId","feeId","feeCode","feeName","amount","currency","dueDate","createdAt"))
            assertThat(cancelled.get(field)).isEqualTo(charge.get(field));
        assertThat(read(call(get(ROOT+"/charges/"+id),null).andExpect(status().isOk()))).isEqualTo(cancelled);
        call(put(ROOT+"/charges/"+id),mutation).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ROOT+"/charges/"+id),Map.of("status","CANCELLED","expectedVersion",1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_FINANCE_STATE"));
        call(put(ROOT+"/charges/"+id),Map.of("status","OPEN","expectedVersion",1)).andExpect(status().isConflict());
        for (long version : List.of(0L,1L)) {
            var event = jdbc.queryForMap("SELECT * FROM finance_audit_events WHERE target_id=? AND resource_version=?",id,version);
            assertThat(event).containsEntry("actor_user_id",actor).containsEntry("resource_type","CHARGE").containsEntry("action",version==0?"CREATED":"CANCELLED");
            assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status",version==0?"OPEN":"CANCELLED"));
        }
    }
    @Test void rejectsMissingInactiveReferencesDuplicateNumbersAndStaleFeeUpdates() throws Exception {
        call(post(ROOT+"/charges"),Map.of("chargeNumber",prefix+"MI","studentId",UUID.randomUUID(),"feeId",fee,"dueDate",DUE)).andExpect(status().isConflict());
        call(post(ROOT+"/charges"),Map.of("chargeNumber",prefix+"MF","studentId",student,"feeId",UUID.randomUUID(),"dueDate",DUE)).andExpect(status().isNotFound());
        var created = read(call(post(ROOT+"/charges"),chargeBody(prefix+"CH")).andExpect(status().isCreated()));
        long before = events(); var original = rows();
        call(post(ROOT+"/charges"),chargeBody(prefix.toLowerCase(Locale.ROOT)+"ch")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINANCE_CODE_ALREADY_EXISTS"));
        call(post(ROOT+"/fees"),Map.of("code",prefix.toLowerCase(Locale.ROOT),"name","Duplicate","amount",10)).andExpect(status().isConflict());
        call(put(ROOT+"/fees/"+fee),feeBody("DIFFERENT",FeeStatus.ACTIVE,99)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ROOT+"/charges/"+created.get("id").asText()),Map.of("status","CANCELLED","expectedVersion",-1)).andExpect(status().isBadRequest());
        assertThat(rows()).isEqualTo(original); assertThat(events()).isEqualTo(before);
        var profile = students.get(student);
        students.update(student,profile.studentNumber(),profile.fullName(),profile.email(),profile.identityUserId(),unit,StudentStatus.INACTIVE,profile.rowVersion());
        call(post(ROOT+"/charges"),chargeBody(prefix+"IN")).andExpect(status().isConflict());
        service.updateFee(actor,fee,prefix,"Fee",BigDecimal.TEN,FeeStatus.INACTIVE,0);
        call(post(ROOT+"/charges"),chargeBody(prefix+"IF")).andExpect(status().isConflict());
    }
    @Test void feeMutationReturnsStoredVersionAndAuditsTrustedActor() throws Exception {
        var request = new LinkedHashMap<String,Object>(feeBody(prefix+"NEW",FeeStatus.INACTIVE,0)); request.put("actorUserId",UUID.randomUUID());
        var response = read(call(put(ROOT+"/fees/"+fee),request).andExpect(status().isOk()));
        assertThat(response.get("rowVersion").asLong()).isEqualTo(1);
        assertThat(read(call(get(ROOT+"/fees/"+fee),null).andExpect(status().isOk()))).isEqualTo(response);
        var event = jdbc.queryForMap("SELECT * FROM finance_audit_events WHERE target_id=? AND action='UPDATED'",fee);
        assertThat(event).containsEntry("actor_user_id",actor).containsEntry("resource_version",1L).containsEntry("resource_type","FEE");
        assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status","INACTIVE"));
    }
    @Test void auditFailureRollsBackAllFourMutationPaths() throws Exception {
        var charge = service.createCharge(actor,prefix+"CH",student,fee,DUE);
        var before = rows(); long events = events(); rejectAudit();
        try {
            call(post(ROOT+"/fees"),Map.of("code",prefix+"NO","name","Fee","amount",10)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            call(put(ROOT+"/fees/"+fee),feeBody(prefix+"NEW",FeeStatus.INACTIVE,0)).andExpect(status().isInternalServerError());
            call(post(ROOT+"/charges"),chargeBody(prefix+"NO")).andExpect(status().isInternalServerError());
            call(put(ROOT+"/charges/"+charge.id()),Map.of("status","CANCELLED","expectedVersion",0)).andExpect(status().isInternalServerError());
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(events);
        } finally { allowAudit(); }
        assertThat(service.cancelCharge(actor,charge.id(),0).rowVersion()).isEqualTo(1);
    }
    @Test void competingFeeUpdatesCommitOneVersionAndEvent() throws Exception {
        long before = events();
        oneSuccess(race(() -> service.updateFee(actor,fee,prefix,"First",BigDecimal.TEN,FeeStatus.ACTIVE,0),
                () -> service.updateFee(actor,fee,prefix,"Second",BigDecimal.ONE,FeeStatus.ACTIVE,0)),FinanceObligationService.StaleVersionException.class);
        assertThat(service.fee(fee).rowVersion()).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
    }
    @Test void feeClosureAndChargeCreationSerializeSnapshotEligibility() throws Exception {
        long before = events();
        var outcomes = race(() -> service.updateFee(actor,fee,prefix,"Fee",BigDecimal.TEN,FeeStatus.INACTIVE,0),
                () -> service.createCharge(actor,prefix+"CH",student,fee,DUE));
        assertThat(outcomes.get(0)).isNull();
        if (outcomes.get(1)!=null) assertThat(outcomes.get(1)).isInstanceOf(FinanceObligationService.ReferenceUnavailableException.class);
        var count = jdbc.queryForObject("SELECT count(*) FROM finance_student_charges WHERE fee_id=?",Long.class,fee);
        assertThat(count).isEqualTo(outcomes.get(1)==null?1L:0L); assertThat(events()).isEqualTo(before+(outcomes.get(1)==null?2:1));
        if(count==1) assertThat(jdbc.queryForObject("SELECT amount FROM finance_student_charges WHERE fee_id=?",BigDecimal.class,fee)).isEqualByComparingTo("100000");
    }
    @Test void cachedFeeRefreshSeesCommittedDeactivationBeforeChargeAdmission() throws Exception {
        var pool = Executors.newSingleThreadExecutor(); long before = events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                assertThat(service.fee(fee).status()).isEqualTo(FeeStatus.ACTIVE);
                var closed = pool.submit(() -> service.updateFee(actor,fee,prefix,"Fee",BigDecimal.TEN,FeeStatus.INACTIVE,0));
                try { assertThat(closed.get(10,TimeUnit.SECONDS).status()).isEqualTo(FeeStatus.INACTIVE); }
                catch(Exception failure) { throw new IllegalStateException(failure); }
                assertThatThrownBy(() -> service.createCharge(actor,prefix+"CACHE",student,fee,DUE)).isInstanceOf(FinanceObligationService.ReferenceUnavailableException.class);
                transaction.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_student_charges WHERE fee_id=?",Long.class,fee)).isZero();
            assertThat(events()).isEqualTo(before+1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void competingCancellationAndDuplicateCreationCommitOnlyOneMutation() throws Exception {
        var charge = service.createCharge(actor,prefix+"CH",student,fee,DUE); long before = events();
        oneSuccess(race(() -> service.cancelCharge(actor,charge.id(),0),() -> service.cancelCharge(actor,charge.id(),0)),FinanceObligationService.StaleVersionException.class);
        assertThat(service.charge(charge.id()).rowVersion()).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
        before = events();
        oneSuccess(race(() -> service.createCharge(actor,prefix+"DU",student,fee,DUE),
                () -> service.createCharge(actor,prefix+"DU",student,fee,DUE)),org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(events()).isEqualTo(before+1);
    }
    @Test void productionFeeLockTimesOutOnSeparateTransactionAndSucceedsAfterRelease() throws Exception {
        var pool = Executors.newSingleThreadExecutor(); long before = events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(holder -> {
                repository.lockFee(fee);
                var future = pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(contender -> {
                    jdbc.execute("SET LOCAL lock_timeout='500ms'"); service.createCharge(actor,prefix+"CH",student,fee,DUE);
                })));
                try {
                    Throwable failure = future.get(10,TimeUnit.SECONDS); assertThat(failure).isNotNull();
                    while(failure.getCause()!=null) failure=failure.getCause();
                    assertThat(failure).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException)failure).getSQLState()).isEqualTo("55P03");
                } catch(Exception failure) { throw new IllegalStateException(failure); }
            });
            assertThat(events()).isEqualTo(before); assertThat(service.createCharge(actor,prefix+"CH",student,fee,DUE).status()).isEqualTo(ChargeStatus.OPEN);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void queryFiltersLiteralSearchStableTiesSortsAndOpenApiAreConsistent() throws Exception {
        var one = service.createCharge(actor,prefix+"A",student,fee,DUE); var two = service.createCharge(actor,prefix+"B",student,fee,DUE);
        var ordered = jdbc.queryForList("SELECT id FROM finance_student_charges WHERE student_id=? ORDER BY id",UUID.class,student);
        for(int page=0;page<2;page++) {
            var value = read(call(get(ROOT+"/charges").param("studentId",student.toString()).param("feeId",fee.toString()).param("size","1").param("page",Integer.toString(page)).param("sort","dueDate,asc"),null).andExpect(status().isOk()));
            assertThat(value.get("content").get(0).get("id").asText()).isEqualTo(ordered.get(page).toString());
        }
        for(String field : List.of("chargeNumber","amount","dueDate","status","createdAt","updatedAt")) for(String order : List.of("asc","desc"))
            call(get(ROOT+"/charges").param("studentId",student.toString()).param("q",prefix).param("status","OPEN").param("sort",field+","+order),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for(String field : List.of("code","name","amount","status","createdAt","updatedAt")) for(String order : List.of("asc","desc"))
            call(get(ROOT+"/fees").param("q",prefix).param("status","ACTIVE").param("sort",field+","+order),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        call(get(ROOT+"/charges").param("studentId",student.toString()).param("q","%"),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        var spec = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String resource : List.of("fees","charges")) for(var operation : List.of(spec.path("paths").path(ROOT+"/"+resource).path("post"),spec.path("paths").path(ROOT+"/"+resource).path("get"),
                spec.path("paths").path(ROOT+"/"+resource+"/{id}").path("get"),spec.path("paths").path(ROOT+"/"+resource+"/{id}").path("put")))
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
        var schema = spec.path("components").path("schemas").path("FinanceChargeCancel");
        assertThat(schema.path("properties").has("expectedVersion")).isTrue(); assertThat(schema.path("properties").has("amount")).isFalse();
    }
    private Map<String,Object> chargeBody(String number) { return Map.of("chargeNumber",number,"studentId",student,"feeId",fee,"dueDate",DUE); }
    private Map<String,Object> feeBody(String code,FeeStatus status,long version) { return Map.of("code",code,"name","Fee","amount",10,"status",status,"expectedVersion",version); }
    private UserAccount account(RoleCode role) { return users.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Admin",passwords.encode("valid-password"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private long events() { return jdbc.queryForObject("SELECT count(*) FROM finance_audit_events WHERE actor_user_id=?",Long.class,actor); }
    private Map<String,List<Map<String,Object>>> rows() {
        return Map.of("fees",jdbc.queryForList("SELECT * FROM finance_fee_definitions ORDER BY id"),
                "charges",jdbc.queryForList("SELECT * FROM finance_student_charges ORDER BY id"));
    }
    private ResultActions call(MockHttpServletRequestBuilder request,Object body) throws Exception {
        request.header("Authorization","Bearer "+admin).contentType("application/json"); if(body!=null) request.content(json.writeValueAsString(body)); return mvc.perform(request);
    }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_finance_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_finance_audit_test BEFORE INSERT ON finance_audit_events FOR EACH ROW EXECUTE FUNCTION reject_finance_audit_test()");
    }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_finance_audit_test ON finance_audit_events"); }
    private List<Throwable> race(Supplier<?> left,Supplier<?> right) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Throwable>>();
            for(var task:List.of(left,right)) futures.add(pool.submit(() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout"); return catchThrowable(task::get); }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return Arrays.asList(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private void oneSuccess(List<Throwable> results,Class<?> failureType) {
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results).filteredOn(Objects::nonNull).singleElement().satisfies(failure -> assertThat(failure).isInstanceOf(failureType));
    }
}
