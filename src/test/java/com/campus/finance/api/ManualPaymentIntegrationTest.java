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
class ManualPaymentIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManualPaymentService service;
    @Autowired FinanceObligationService obligations;
    @Autowired FinanceRepository charges;
    @Autowired PaymentRepository payments;
    @Autowired FinanceAudit audit;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService units;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired PlatformTransactionManager transactions;
    UUID actor,unit,student,fee,charge;
    String admin,user,prefix;
    static final String ROOT="/api/v1/admin/finance";
    @BeforeEach void setup() {
        prefix=UUID.randomUUID().toString().substring(0,8);
        var account=account(RoleCode.ADMIN); actor=account.id(); admin=tokens.accessToken(account); user=tokens.accessToken(account(RoleCode.USER));
        unit=units.create(prefix,"Unit",OrganizationUnitType.FACULTY,OrganizationUnitStatus.ACTIVE).id();
        student=students.create(prefix,"Student",null,null,unit,StudentStatus.ACTIVE).id();
        fee=obligations.createFee(actor,prefix,"Fee",new BigDecimal("100")).id();
        charge=obligations.createCharge(actor,prefix,student,fee,LocalDate.of(2026,1,1)).id();
    }
    @Test void protectsAllFiveOperationsAndInvalidInputsDoNotWrite() throws Exception {
        long before=events(); var rows=rows();
        for(String bearer:List.of("",user)) for(var request:List.of(get(ROOT+"/payments"),get(ROOT+"/payments/"+UUID.randomUUID()),post(ROOT+"/payments"),put(ROOT+"/payments/"+UUID.randomUUID()),get(ROOT+"/charges/"+charge+"/balance"))) {
            if(!bearer.isEmpty()) request.header("Authorization","Bearer "+bearer);
            mvc.perform(request.contentType("application/json").content("{}")).andExpect(status().is(bearer.isEmpty()?401:403));
        }
        call(post(ROOT+"/payments"),Map.of()).andExpect(status().isBadRequest());
        call(put(ROOT+"/payments/"+UUID.randomUUID()),Map.of()).andExpect(status().isBadRequest());
        call(get(ROOT+"/payments/bad-id"),null).andExpect(status().isBadRequest());
        call(get(ROOT+"/payments/"+UUID.randomUUID()),null).andExpect(status().isNotFound());
        call(get(ROOT+"/charges/"+UUID.randomUUID()+"/balance"),null).andExpect(status().isNotFound());
        for(var entry:List.of(Map.entry("page","-1"),Map.entry("page","2147483647"),Map.entry("size","0"),Map.entry("size","101"),Map.entry("q","x".repeat(101)),
                Map.entry("status","UNKNOWN"),Map.entry("sort","id,asc"),Map.entry("sort","amount,wrong"),Map.entry("chargeId","bad-id")))
            call(get(ROOT+"/payments").param(entry.getKey(),entry.getValue()),null).andExpect(status().isBadRequest());
        for(String amount:List.of("0","-1","0.001","10000000000000000000"))
            call(post(ROOT+"/payments"),createBody(prefix+"BAD",new BigDecimal(amount),0)).andExpect(status().isBadRequest());
        call(post(ROOT+"/payments"),createBody(prefix+"BAD",BigDecimal.ONE,-1)).andExpect(status().isBadRequest());
        assertThat(events()).isEqualTo(before); assertThat(rows()).isEqualTo(rows);
    }
    @Test void partialThenFullPaymentReversalAndCancellationRetainImmutableHistory() throws Exception {
        var body=new LinkedHashMap<String,Object>(createBody(prefix+"ONE",new BigDecimal("40"),0)); body.put("actorUserId",UUID.randomUUID());
        var first=read(call(post(ROOT+"/payments"),body).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        UUID one=UUID.fromString(first.get("id").asText());
        assertThat(read(call(get(ROOT+"/payments/"+one),null).andExpect(status().isOk()))).isEqualTo(first);
        balance("40","60",1); long before=events(); var snapshot=rows();
        call(put(ROOT+"/charges/"+charge),Map.of("status","CANCELLED","expectedVersion",1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_FINANCE_STATE"));
        call(post(ROOT+"/payments"),createBody(prefix+"OVER",new BigDecimal("61"),1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_EXCEEDS_BALANCE"));
        call(post(ROOT+"/payments"),createBody(prefix+"STALE",BigDecimal.ONE,0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        assertThat(rows()).isEqualTo(snapshot); assertThat(events()).isEqualTo(before);
        var second=service.record(actor,prefix+"TWO",charge,new BigDecimal("60"),1); balance("100","0",2);
        call(post(ROOT+"/payments"),createBody(prefix+"EXTRA",BigDecimal.ONE,2)).andExpect(status().isConflict());
        var reverse=new LinkedHashMap<String,Object>(reverseBody(0,2," Correction "));
        reverse.put("amount",1); reverse.put("chargeId",UUID.randomUUID());
        var reversed=read(call(put(ROOT+"/payments/"+one),reverse).andExpect(status().isOk()));
        for(String field:List.of("id","receiptNumber","chargeId","amount","currency","recordedAt","createdAt")) assertThat(reversed.get(field)).isEqualTo(first.get(field));
        assertThat(reversed.get("rowVersion").asLong()).isEqualTo(1); assertThat(reversed.get("reversalReason").asText()).isEqualTo("Correction");
        assertThat(read(call(get(ROOT+"/payments/"+one),null).andExpect(status().isOk()))).isEqualTo(reversed);
        balance("60","40",3);
        call(put(ROOT+"/payments/"+one),reverseBody(0,3,"Again")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        call(put(ROOT+"/payments/"+one),reverseBody(1,3,"Again")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_FINANCE_STATE"));
        call(post(ROOT+"/payments"),createBody(prefix.toLowerCase(Locale.ROOT)+"one",BigDecimal.ONE,3)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINANCE_CODE_ALREADY_EXISTS"));
        service.reverse(actor,second.id(),0,3,"Second correction"); balance("0","100",4);
        obligations.cancelCharge(actor,charge,4);
        assertThat(service.balance(charge).status()).isEqualTo(ChargeStatus.CANCELLED);
        balance("0","0",5);
        call(get(ROOT+"/charges/"+charge+"/balance"),null).andExpect(status().isOk()).andExpect(jsonPath("$.outstandingAmount").value(0))
                .andExpect(jsonPath("$.amount").value(100)).andExpect(jsonPath("$.rowVersion").value(5));
        call(post(ROOT+"/payments"),createBody(prefix+"CANCELLED",BigDecimal.ONE,5)).andExpect(status().isConflict());
        for(long version:List.of(0L,1L)) {
            var event=jdbc.queryForMap("SELECT * FROM finance_audit_events WHERE target_id=? AND resource_version=?",one,version);
            assertThat(event).containsEntry("actor_user_id",actor).containsEntry("resource_type","PAYMENT").containsEntry("action",version==0?"RECORDED":"REVERSED");
            assertThat(json.readTree(event.get("metadata").toString())).isEqualTo(json.createObjectNode().put("status",version==0?"RECORDED":"REVERSED"));
        }
    }
    @Test void validatesReversalReasonStateAndVersionsWithoutMutating() throws Exception {
        var payment=service.record(actor,prefix+"ONE",charge,BigDecimal.ONE,0); var before=rows(); long events=events();
        for(String reason:List.of("x"," \t\n\r\u000b\f ","😀".repeat(501)))
            call(put(ROOT+"/payments/"+payment.id()),reverseBody(0,1,reason)).andExpect(status().isBadRequest());
        call(put(ROOT+"/payments/"+payment.id()),reverseBody(-1,1,"Reason")).andExpect(status().isBadRequest());
        call(put(ROOT+"/payments/"+payment.id()),reverseBody(0,-1,"Reason")).andExpect(status().isBadRequest());
        call(put(ROOT+"/payments/"+payment.id()),Map.of("status","RECORDED","expectedVersion",0,"expectedChargeVersion",1,"reason","Reason")).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(events);
        assertThat(service.reverse(actor,payment.id(),0,1,"😀".repeat(500)).reversalReason()).hasSize(1000);
    }
    @Test void allowsSettlementAndReversalAfterStudentAndFeeDeactivation() {
        var profile=students.get(student);
        students.update(student,profile.studentNumber(),profile.fullName(),profile.email(),profile.identityUserId(),unit,StudentStatus.INACTIVE,profile.rowVersion());
        obligations.updateFee(actor,fee,prefix,"Fee",BigDecimal.ONE,FeeStatus.INACTIVE,0);
        var payment=service.record(actor,prefix+"ONE",charge,BigDecimal.TEN,0);
        assertThat(service.reverse(actor,payment.id(),0,1,"Correction").status()).isEqualTo(PaymentStatus.REVERSED);
        obligations.cancelCharge(actor,charge,2);
    }
    @Test void exactMaximumPaymentAndBalanceDoNotOverflow() {
        var maximum=obligations.createFee(actor,prefix+"MAX","Maximum",FinanceValues.MAX_AMOUNT);
        var large=obligations.createCharge(actor,prefix+"MAX",student,maximum.id(),LocalDate.now());
        var payment=service.record(actor,prefix+"MAX",large.id(),FinanceValues.MAX_AMOUNT,0);
        assertThat(service.get(payment.id()).amount()).isEqualTo(FinanceValues.MAX_AMOUNT);
        assertThat(service.balance(large.id()).paidAmount()).isEqualTo(FinanceValues.MAX_AMOUNT);
        assertThat(service.balance(large.id()).outstandingAmount()).isEqualTo(BigDecimal.ZERO);
    }
    @Test void coincidentClockTimesStillAdvanceChargeVersionOnPaymentAndReversal() {
        Instant same=obligations.charge(charge).createdAt().plusSeconds(1);
        var fixed=new ManualPaymentService(charges,payments,audit,Clock.fixed(same,ZoneOffset.UTC));
        var payment=new TransactionTemplate(transactions).execute(transaction -> fixed.record(actor,prefix+"ONE",charge,BigDecimal.ONE,0));
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> fixed.reverse(actor,payment.id(),0,1,"Correction"));
        assertThat(service.balance(charge).rowVersion()).isEqualTo(2);
        assertThat(service.balance(charge).paidAmount()).isEqualTo(BigDecimal.ZERO);
    }
    @Test void auditFailureRollsBackPaymentReversalChargeVersionAndBalance() throws Exception {
        long before=events(); var snapshot=rows(); rejectAudit();
        try {
            call(post(ROOT+"/payments"),createBody(prefix+"ONE",BigDecimal.TEN,0)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(rows()).isEqualTo(snapshot); assertThat(events()).isEqualTo(before); balance("0","100",0);
        } finally { allowAudit(); }
        var payment=service.record(actor,prefix+"ONE",charge,BigDecimal.TEN,0); before=events(); snapshot=rows(); rejectAudit();
        try {
            call(put(ROOT+"/payments/"+payment.id()),reverseBody(0,1,"Correction")).andExpect(status().isInternalServerError());
            assertThat(rows()).isEqualTo(snapshot); assertThat(events()).isEqualTo(before); balance("10","90",1);
        } finally { allowAudit(); }
    }
    @Test void competingFinalPaymentsCannotOverpayAndHaveOneEvent() throws Exception {
        long before=events();
        oneSuccess(race(() -> service.record(actor,prefix+"ONE",charge,new BigDecimal("60"),0),
                () -> service.record(actor,prefix+"TWO",charge,new BigDecimal("60"),0)),FinanceObligationService.StaleVersionException.class);
        balance("60","40",1); assertThat(events()).isEqualTo(before+1);
    }
    @Test void duplicateReceiptAcrossDifferentChargesIsRejectedAtomically() throws Exception {
        var other=obligations.createCharge(actor,prefix+"OTHER",student,fee,LocalDate.now()); long before=events();
        oneSuccess(race(() -> service.record(actor,prefix+"SAME",charge,BigDecimal.TEN,0),
                () -> service.record(actor,prefix+"SAME",other.id(),BigDecimal.TEN,0)),org.springframework.dao.DataIntegrityViolationException.class);
        long versions=obligations.charge(charge).rowVersion()+obligations.charge(other.id()).rowVersion();
        assertThat(versions).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
        assertThat(service.balance(charge).paidAmount().add(service.balance(other.id()).paidAmount())).isEqualTo(BigDecimal.TEN);
    }
    @Test void competingReversalsCommitOneHistoryChangeAndEvent() throws Exception {
        var payment=service.record(actor,prefix+"ONE",charge,BigDecimal.TEN,0); long before=events();
        oneSuccess(race(() -> service.reverse(actor,payment.id(),0,1,"First correction"),
                () -> service.reverse(actor,payment.id(),0,1,"Second correction")),FinanceObligationService.StaleVersionException.class);
        balance("0","100",2); assertThat(service.get(payment.id()).rowVersion()).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
    }
    @Test void cancellationAndPaymentShareChargeLockAndRemainConsistent() throws Exception {
        long before=events();
        oneSuccess(race(() -> obligations.cancelCharge(actor,charge,0),() -> service.record(actor,prefix+"ONE",charge,BigDecimal.TEN,0)),FinanceObligationService.StaleVersionException.class);
        var balance=service.balance(charge);
        if(balance.status()==ChargeStatus.CANCELLED) assertThat(balance.paidAmount()).isZero();
        else assertThat(balance.paidAmount()).isEqualTo(BigDecimal.TEN);
        assertThat(balance.rowVersion()).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
    }
    @Test void reversalAndNewPaymentPreserveHistoryAndRejectStaleBalance() throws Exception {
        var payment=service.record(actor,prefix+"ONE",charge,new BigDecimal("100"),0); long before=events();
        var outcomes=race(() -> service.reverse(actor,payment.id(),0,1,"Correction"),() -> service.record(actor,prefix+"TWO",charge,new BigDecimal("100"),1));
        assertThat(outcomes.get(0)).isNull(); assertThat(outcomes.get(1)).isInstanceOfAny(ManualPaymentService.ExceedsBalanceException.class,FinanceObligationService.StaleVersionException.class);
        balance("0","100",2); assertThat(events()).isEqualTo(before+1);
        service.record(actor,prefix+"TWO",charge,new BigDecimal("100"),2); balance("100","0",3);
        assertThat(service.get(payment.id()).status()).isEqualTo(PaymentStatus.REVERSED);
    }
    @Test void cachedChargeAndReceiptRefreshSeeCommittedChanges() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                assertThat(obligations.charge(charge).rowVersion()).isZero();
                var paid=pool.submit(() -> service.record(actor,prefix+"ONE",charge,BigDecimal.TEN,0));
                try { paid.get(10,TimeUnit.SECONDS); } catch(Exception failure) { throw new IllegalStateException(failure); }
                assertThatThrownBy(() -> service.record(actor,prefix+"TWO",charge,BigDecimal.TEN,0)).isInstanceOf(FinanceObligationService.StaleVersionException.class);
                transaction.setRollbackOnly();
            });
            UUID id=jdbc.queryForObject("SELECT id FROM finance_manual_payments WHERE charge_id=?",UUID.class,charge);
            new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                assertThat(service.get(id).rowVersion()).isZero();
                var reversed=pool.submit(() -> service.reverse(actor,id,0,1,"Correction"));
                try { reversed.get(10,TimeUnit.SECONDS); } catch(Exception failure) { throw new IllegalStateException(failure); }
                assertThatThrownBy(() -> service.reverse(actor,id,0,2,"Again")).isInstanceOf(FinanceObligationService.StaleVersionException.class);
                transaction.setRollbackOnly();
            });
            balance("0","100",2);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void productionChargeLockTimesOutThenAcquiresAfterRelease() throws Exception {
        var pool=Executors.newSingleThreadExecutor(); long before=events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(holder -> {
                charges.lockCharge(charge);
                var future=pool.submit(() -> catchThrowable(() -> new TransactionTemplate(transactions).executeWithoutResult(contender -> {
                    jdbc.execute("SET LOCAL lock_timeout='500ms'"); service.record(actor,prefix+"ONE",charge,BigDecimal.ONE,0);
                })));
                try {
                    Throwable failure=future.get(10,TimeUnit.SECONDS); assertThat(failure).isNotNull();
                    while(failure.getCause()!=null) failure=failure.getCause();
                    assertThat(failure).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException)failure).getSQLState()).isEqualTo("55P03");
                } catch(Exception failure) { throw new IllegalStateException(failure); }
            });
            assertThat(events()).isEqualTo(before); balance("0","100",0);
            assertThat(service.record(actor,prefix+"ONE",charge,BigDecimal.ONE,0).status()).isEqualTo(PaymentStatus.RECORDED);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void filtersSortsLiteralSearchActualTiesAndOpenApiAreConsistent() throws Exception {
        var one=service.record(actor,prefix+"ONE",charge,BigDecimal.ONE,0); var two=service.record(actor,prefix+"TWO",charge,BigDecimal.ONE,1);
        jdbc.update("UPDATE finance_manual_payments SET recorded_at=TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE id IN (?,?)",one.id(),two.id());
        var ordered=jdbc.queryForList("SELECT id FROM finance_manual_payments WHERE charge_id=? ORDER BY id",UUID.class,charge);
        for(int page=0;page<2;page++) {
            var value=read(call(get(ROOT+"/payments").param("chargeId",charge.toString()).param("size","1").param("page",Integer.toString(page)).param("sort","recordedAt,desc"),null).andExpect(status().isOk()));
            assertThat(value.get("content").get(0).get("id").asText()).isEqualTo(ordered.get(page).toString());
        }
        for(String field:List.of("receiptNumber","amount","status","recordedAt","createdAt","updatedAt")) for(String order:List.of("asc","desc"))
            call(get(ROOT+"/payments").param("chargeId",charge.toString()).param("status","RECORDED").param("q",prefix).param("sort",field+","+order),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        call(get(ROOT+"/payments").param("chargeId",charge.toString()).param("q","%"),null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(var operation:List.of(spec.path("paths").path(ROOT+"/payments").path("post"),spec.path("paths").path(ROOT+"/payments").path("get"),
                spec.path("paths").path(ROOT+"/payments/{id}").path("get"),spec.path("paths").path(ROOT+"/payments/{id}").path("put"),spec.path("paths").path(ROOT+"/charges/{id}/balance").path("get")))
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
        var update=spec.path("components").path("schemas").path("FinancePaymentReverse");
        assertThat(update.path("properties").has("expectedChargeVersion")).isTrue(); assertThat(update.path("properties").has("amount")).isFalse();
    }
    private Map<String,Object> createBody(String number,BigDecimal amount,long version) { return Map.of("receiptNumber",number,"chargeId",charge,"amount",amount,"expectedChargeVersion",version); }
    private Map<String,Object> reverseBody(long version,long chargeVersion,String reason) { return Map.of("status","REVERSED","expectedVersion",version,"expectedChargeVersion",chargeVersion,"reason",reason); }
    private void balance(String paid,String outstanding,long version) {
        var value=service.balance(charge); assertThat(value.paidAmount()).isEqualByComparingTo(paid); assertThat(value.outstandingAmount()).isEqualByComparingTo(outstanding); assertThat(value.rowVersion()).isEqualTo(version);
    }
    private UserAccount account(RoleCode role) { return users.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Admin",passwords.encode("valid-password"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private long events() { return jdbc.queryForObject("SELECT count(*) FROM finance_audit_events WHERE actor_user_id=?",Long.class,actor); }
    private Map<String,List<Map<String,Object>>> rows() { return Map.of("charges",jdbc.queryForList("SELECT * FROM finance_student_charges ORDER BY id"),"payments",jdbc.queryForList("SELECT * FROM finance_manual_payments ORDER BY id")); }
    private ResultActions call(MockHttpServletRequestBuilder request,Object body) throws Exception { request.header("Authorization","Bearer "+admin).contentType("application/json"); if(body!=null) request.content(json.writeValueAsString(body)); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() {
        jdbc.execute("CREATE OR REPLACE FUNCTION reject_payment_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_payment_audit_test BEFORE INSERT ON finance_audit_events FOR EACH ROW EXECUTE FUNCTION reject_payment_audit_test()");
    }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_payment_audit_test ON finance_audit_events"); }
    private List<Throwable> race(Supplier<?> left,Supplier<?> right) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Throwable>>();
            for(var task:List.of(left,right)) futures.add(pool.submit(() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout"); return catchThrowable(task::get); }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown(); return Arrays.asList(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private void oneSuccess(List<Throwable> results,Class<?> failure) {
        assertThat(results).filteredOn(Objects::isNull).hasSize(1); assertThat(results).filteredOn(Objects::nonNull).singleElement().satisfies(value -> assertThat(value).isInstanceOf(failure));
    }
}
