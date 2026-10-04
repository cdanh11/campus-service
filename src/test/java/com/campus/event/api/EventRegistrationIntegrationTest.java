package com.campus.event.api;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.event.application.*;
import com.campus.event.domain.*;
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
class EventRegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EventCatalogService catalog;
    @Autowired EventRegistrationService registrations;
    @Autowired EventRepository repository;
    @Autowired StudentManagementService students;
    @Autowired OrganizationUnitManagementService organizations;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    UUID actor,student,otherStudent,unit; String admin,user,other,unlinked,prefix; CampusEvent event;
    static final String ADMIN="/api/v1/admin/event-registrations", OWN="/api/v1/event-registrations";
    @BeforeEach void setup() {
        prefix=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        var account=account(RoleCode.ADMIN); actor=account.id(); admin=tokens.accessToken(account);
        unit=organizations.create(prefix,"Event unit",OrganizationUnitType.FACULTY,OrganizationUnitStatus.ACTIVE).id();
        account=account(RoleCode.USER); user=tokens.accessToken(account); student=student(account.id());
        account=account(RoleCode.USER); other=tokens.accessToken(account); otherStudent=student(account.id());
        unlinked=tokens.accessToken(account(RoleCode.USER)); event=open(3);
    }
    @Test void deniesAllRegistrationOperationRolesAndDocumentsBearer() throws Exception {
        String initial="/api/v1/admin/events/"+event.id()+"/registrations";
        for(var request:List.of(post(initial),get(ADMIN),get(ADMIN+"/"+UUID.randomUUID()),put(ADMIN+"/"+UUID.randomUUID()))) call(request,null,"").andExpect(status().isUnauthorized());
        for(var request:List.of(post(initial),get(ADMIN),get(ADMIN+"/"+UUID.randomUUID()),put(ADMIN+"/"+UUID.randomUUID()))) call(request,null,user).andExpect(status().isForbidden());
        for(var request:List.of(post("/api/v1/events/"+event.id()+"/registrations"),get(OWN),get(OWN+"/"+UUID.randomUUID()),put(OWN+"/"+UUID.randomUUID()))) call(request,null,"").andExpect(status().isUnauthorized());
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String path:List.of("/api/v1/admin/events/{eventId}/registrations",ADMIN,ADMIN+"/{id}","/api/v1/events/{eventId}/registrations",OWN,OWN+"/{id}"))
            spec.path("paths").path(path).elements().forEachRemaining(operation->assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue());
    }
    @Test void ownsMembershipIgnoresSpoofingRestoresSameIdAndKeepsTerminalAttendanceHistory() throws Exception {
        var created=read(call(post("/api/v1/events/"+event.id()+"/registrations"),Map.of("studentId",otherStudent,"actor",actor),user).andExpect(status().isCreated()).andExpect(header().exists("Location")));
        UUID id=UUID.fromString(created.path("id").asText());
        assertThat(created.path("studentId").asText()).isEqualTo(student.toString());
        assertThat(created).isEqualTo(read(call(get(OWN+"/"+id),null,user).andExpect(status().isOk())));
        call(get(OWN+"/"+id),null,other).andExpect(status().isNotFound());
        call(put(OWN+"/"+id),change("CANCEL",0),other).andExpect(status().isNotFound());
        call(put(OWN+"/"+id),change("ATTEND",0),user).andExpect(status().isForbidden());
        call(post("/api/v1/events/"+event.id()+"/registrations"),null,user).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_ALREADY_EXISTS"));
        call(put(OWN+"/"+id),change("CANCEL",0),user).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(1));
        call(put(OWN+"/"+id),change("RESTORE",0),user).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        var restored=read(call(put(OWN+"/"+id),change("RESTORE",1),user).andExpect(status().isOk()).andExpect(jsonPath("$.rowVersion").value(2)));
        assertThat(restored.path("id")).isEqualTo(created.path("id")); assertThat(restored.path("createdAt")).isEqualTo(created.path("createdAt"));
        assertThat(restored.path("cancelledAt").isNull()).isTrue();
        catalog.update(actor,event.id(),event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),3,CampusEvent.Status.CLOSED,1);
        call(put(ADMIN+"/"+id),change("ATTEND",2),admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATTENDED")).andExpect(jsonPath("$.rowVersion").value(3));
        call(put(ADMIN+"/"+id),change("CANCEL",3),admin).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_audit_events WHERE target_id=? AND resource_type='REGISTRATION'",Long.class,id)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM event_audit_events WHERE target_id=? AND action='REGISTERED'",UUID.class,id)).isNotEqualTo(actor);
        call(post("/api/v1/events/"+event.id()+"/registrations"),null,other).andExpect(status().isConflict());
    }
    @Test void allowsOpenPastEventAndAdminForUnlinkedStudentButRejectsInactiveAndUnlinkedSelf() throws Exception {
        // Fixture event dates are in the past: OPEN, not startsAt, is the approved admission gate.
        UUID withoutAccount=student(null);
        call(post("/api/v1/admin/events/"+event.id()+"/registrations"),Map.of("studentId",withoutAccount),admin).andExpect(status().isCreated());
        call(post("/api/v1/events/"+event.id()+"/registrations"),Map.of("studentId",student),unlinked).andExpect(status().isNotFound());
        call(post("/api/v1/admin/events/"+event.id()+"/registrations"),Map.of("studentId",UUID.randomUUID()),admin).andExpect(status().isConflict());
        var membership=registrations.register(actor,event.id(),student);
        jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?",student);
        call(put(OWN+"/"+membership.id()),change("CANCEL",0),user).andExpect(status().isOk());
        call(put(OWN+"/"+membership.id()),change("RESTORE",1),user).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_STUDENT_UNAVAILABLE"));
        jdbc.update("UPDATE students SET status='ACTIVE',identity_user_id=NULL WHERE id=?",student);
        call(get(OWN+"/"+membership.id()),null,user).andExpect(status().isNotFound());
        call(put(ADMIN+"/"+membership.id()),change("RESTORE",1),admin).andExpect(status().isOk());
    }
    @Test void enforcesCapacityCountsAttendanceAndRejectsReducingBelowConsumedSeats() {
        event=open(1); var first=registrations.register(actor,event.id(),student);
        assertThatThrownBy(()->registrations.register(actor,event.id(),otherStudent)).isInstanceOf(EventRegistrationService.CapacityExceededException.class);
        registrations.change(actor,first.id(),EventRegistrationService.Action.CANCEL,0);
        var second=registrations.register(actor,event.id(),otherStudent);
        assertThatThrownBy(()->registrations.change(actor,first.id(),EventRegistrationService.Action.RESTORE,1)).isInstanceOf(EventRegistrationService.CapacityExceededException.class);
        registrations.change(actor,second.id(),EventRegistrationService.Action.ATTEND,0);
        assertThatThrownBy(()->registrations.change(actor,first.id(),EventRegistrationService.Action.RESTORE,1)).isInstanceOf(EventRegistrationService.CapacityExceededException.class);
        event=open(3); registrations.register(actor,event.id(),student); registrations.register(actor,event.id(),otherStudent);
        assertThatThrownBy(()->catalog.update(actor,event.id(),event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),1,CampusEvent.Status.OPEN,1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.get(event.id()).capacity()).isEqualTo(3);
    }
    @Test void rollsBackRegisterCancelRestoreAndAttendanceWhenAuditWriteFails() {
        var membership=registrations.register(actor,event.id(),student);
        registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,0);
        var cancelled=rows(); long count=auditCount(); rejectAudit();
        try {
            assertThatThrownBy(()->registrations.register(actor,event.id(),otherStudent)).isInstanceOf(EventAudit.UnavailableException.class);
            assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1)).isInstanceOf(EventAudit.UnavailableException.class);
            assertThat(rows()).isEqualTo(cancelled); assertThat(auditCount()).isEqualTo(count);
        } finally { allowAudit(); }
        registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1);
        var active=rows(); count=auditCount(); rejectAudit();
        try {
            assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,2)).isInstanceOf(EventAudit.UnavailableException.class);
            assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.ATTEND,2)).isInstanceOf(EventAudit.UnavailableException.class);
            assertThat(rows()).isEqualTo(active); assertThat(auditCount()).isEqualTo(count);
        } finally { allowAudit(); }
    }
    @Test void serializesLastSeatAndDuplicateMembershipRaces() throws Exception {
        event=open(1);
        var errors=race(()->registrations.register(actor,event.id(),student),()->registrations.register(actor,event.id(),otherStudent));
        assertThat(errors.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(EventRegistrationService.CapacityExceededException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_registrations WHERE event_id=?",Long.class,event.id())).isEqualTo(1);
        event=open(3);
        errors=race(()->registrations.register(actor,event.id(),student),()->registrations.register(actor,event.id(),student));
        assertThat(errors.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(EventRegistrationService.DuplicateMembershipException.class);
    }
    @Test void restorationCompetesWithNewAdmissionForTheSameFinalSeat() throws Exception {
        event=open(1); var membership=registrations.register(actor,event.id(),student);
        registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,0);
        var errors=race(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1),
                ()->registrations.register(actor,event.id(),otherStudent));
        assertThat(errors.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(EventRegistrationService.CapacityExceededException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_registrations WHERE event_id=? AND status IN ('REGISTERED','ATTENDED')",Long.class,event.id())).isEqualTo(1);
        assertThat(registrations.get(membership.id()).id()).isEqualTo(membership.id());
    }
    @Test void rejectsDraftAdmissionAndCancelledEventAttendanceButAllowsRetainedCancellation() {
        var start=Instant.parse("2025-01-01T08:00:00Z");
        var draft=catalog.create(actor,prefix+"-DRAFT","Draft event","Description",start,start.plusSeconds(3600),3);
        assertThatThrownBy(()->registrations.register(actor,draft.id(),student)).isInstanceOf(EventCatalogService.InvalidStateException.class);
        var membership=registrations.register(actor,event.id(),student);
        catalog.update(actor,event.id(),event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),3,CampusEvent.Status.CANCELLED,1);
        assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.ATTEND,0)).isInstanceOf(EventCatalogService.InvalidStateException.class);
        assertThat(registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,0).status()).isEqualTo(EventRegistration.Status.CANCELLED);
        assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1)).isInstanceOf(EventCatalogService.InvalidStateException.class);
    }
    @Test void filtersOwnerQueriesAndRejectsMalformedVersionsAndStudentSpoofing() throws Exception {
        var mine=registrations.register(actor,event.id(),student); registrations.register(actor,event.id(),otherStudent);
        call(get(OWN).param("studentId",otherStudent.toString()),null,user).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(mine.id().toString()));
        call(get(ADMIN).param("eventId",event.id().toString()).param("studentId",otherStudent.toString()),null,admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for(String field:List.of("registeredAt","status","createdAt","updatedAt")) for(String direction:List.of("asc","desc"))
            call(get(OWN).param("sort",field+","+direction),null,user).andExpect(status().isOk());
        for(Object version:List.of(-1,0.5,"0",new java.math.BigInteger("9223372036854775808"))) {
            var body=new LinkedHashMap<String,Object>(); body.put("action","CANCEL"); body.put("expectedVersion",version);
            call(put(OWN+"/"+mine.id()),body,user).andExpect(status().isBadRequest());
        }
        call(get(OWN).param("sort","studentId,asc"),null,user).andExpect(status().isBadRequest());
        call(get(ADMIN).param("size","101"),null,admin).andExpect(status().isBadRequest());
        call(get(OWN).param("status","UNKNOWN"),null,user).andExpect(status().isBadRequest());
        assertThat(registrations.get(mine.id()).rowVersion()).isZero();
    }
    @Test void concurrentRestoresAndCancelVersusAttendanceAdvanceOnlyOneVersion() throws Exception {
        var membership=registrations.register(actor,event.id(),student);
        registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,0);
        var errors=race(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1),
                ()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1));
        assertOneStale(errors); assertThat(registrations.get(membership.id()).rowVersion()).isEqualTo(2);
        errors=race(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,2),
                ()->registrations.change(actor,membership.id(),EventRegistrationService.Action.ATTEND,2));
        assertOneStale(errors); assertThat(registrations.get(membership.id()).rowVersion()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_audit_events WHERE target_id=?",Long.class,membership.id())).isEqualTo(4);
    }
    @Test void serializesEventClosureAndCapacityReductionAgainstAdmission() throws Exception {
        var errors=race(()->registrations.register(actor,event.id(),student),
                ()->catalog.update(actor,event.id(),event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),3,CampusEvent.Status.CLOSED,1));
        assertThat(catalog.get(event.id()).status()).isEqualTo(CampusEvent.Status.CLOSED);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).allSatisfy(error->assertThat(error).isInstanceOf(EventCatalogService.InvalidStateException.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_registrations WHERE event_id=?",Long.class,event.id())).isBetween(0L,1L);
        event=open(3); registrations.register(actor,event.id(),student);
        errors=race(()->registrations.register(actor,event.id(),otherStudent),
                ()->catalog.update(actor,event.id(),event.code(),event.title(),event.description(),event.startsAt(),event.endsAt(),1,CampusEvent.Status.OPEN,1));
        assertThat(errors.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).singleElement().satisfies(error->assertThat(error instanceof IllegalArgumentException || error instanceof EventRegistrationService.CapacityExceededException).isTrue());
        long count=jdbc.queryForObject("SELECT count(*) FROM event_registrations WHERE event_id=? AND status IN ('REGISTERED','ATTENDED')",Long.class,event.id());
        assertThat(count).isLessThanOrEqualTo(catalog.get(event.id()).capacity());
    }
    @Test void seesCachedMembershipAndStudentChangesAndTimesOutMembershipLockThenRecovers() throws Exception {
        var membership=registrations.register(actor,event.id(),student); var worker=Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                assertThat(registrations.get(membership.id()).rowVersion()).isZero();
                try { worker.submit(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.CANCEL,0)).get(10,TimeUnit.SECONDS); }
                catch(Exception failure){throw new AssertionError(failure);}
                assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,0)).isInstanceOf(EventCatalogService.StaleVersionException.class);
                tx.setRollbackOnly();
            });
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                assertThat(students.get(student).status()).isEqualTo(StudentStatus.ACTIVE);
                try { worker.submit(()->jdbc.update("UPDATE students SET status='INACTIVE' WHERE id=?",student)).get(10,TimeUnit.SECONDS); }
                catch(Exception failure){throw new AssertionError(failure);}
                assertThatThrownBy(()->registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1)).isInstanceOf(EventRegistrationService.StudentUnavailableException.class);
                tx.setRollbackOnly();
            });
            jdbc.update("UPDATE students SET status='ACTIVE' WHERE id=?",student);
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                repository.lockRegistration(membership.id());
                try {
                    var failure=worker.submit(()->catchThrowable(()->new TransactionTemplate(transactions).executeWithoutResult(competing->{
                        jdbc.execute("SET LOCAL lock_timeout='500ms'"); registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1);
                    }))).get(10,TimeUnit.SECONDS);
                    assertThat(failure).isNotNull(); boolean expected=false;
                    for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof java.sql.SQLException sql && "55P03".equals(sql.getSQLState())) expected=true;
                    assertThat(expected).isTrue();
                } catch(Exception failure){throw new AssertionError(failure);}
            });
            assertThat(registrations.change(actor,membership.id(),EventRegistrationService.Action.RESTORE,1).rowVersion()).isEqualTo(2);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void pagesActualRegistrationTimestampTiesAndBoundsBothQuerySurfaces() throws Exception {
        var first=registrations.register(actor,event.id(),student); var second=registrations.register(actor,event.id(),otherStudent);
        jdbc.update("UPDATE event_registrations SET registered_at=TIMESTAMPTZ '2026-01-01 00:00:00Z' WHERE event_id=?",event.id());
        var ids=jdbc.queryForList("SELECT id FROM event_registrations WHERE event_id=? ORDER BY id",UUID.class,event.id());
        for(int page=0;page<2;page++) call(get(ADMIN).param("eventId",event.id().toString()).param("status","REGISTERED").param("sort","registeredAt,asc").param("size","1").param("page",String.valueOf(page)),null,admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(ids.get(page).toString()));
        for(String path:List.of(ADMIN,OWN)) {
            String token=path.equals(ADMIN)?admin:user;
            for(String field:List.of("registeredAt","status","createdAt","updatedAt")) for(String direction:List.of("asc","desc"))
                call(get(path).param("eventId",event.id().toString()).param("sort",field+","+direction),null,token).andExpect(status().isOk());
            for(var invalid:List.of(Map.of("page","-1"),Map.of("page","2147483647","size","100"),Map.of("sort","registeredAt,bad"),Map.of("status","UNKNOWN"))) {
                var request=get(path); invalid.forEach(request::param); call(request,null,token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
            }
        }
        assertThat(registrations.get(first.id()).studentId()).isEqualTo(student); assertThat(registrations.get(second.id()).studentId()).isEqualTo(otherStudent);
    }
    private void assertOneStale(List<Throwable> errors) {
        assertThat(errors.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(errors.stream().filter(Objects::nonNull).toList()).singleElement().isInstanceOf(EventCatalogService.StaleVersionException.class);
    }
    private CampusEvent open(int capacity) {
        var start=Instant.parse("2025-01-01T08:00:00Z");
        var value=catalog.create(actor,prefix+"-"+UUID.randomUUID().toString().substring(0,8),"Event title","Event description",start,start.plusSeconds(7200),capacity);
        return catalog.update(actor,value.id(),value.code(),value.title(),value.description(),value.startsAt(),value.endsAt(),capacity,CampusEvent.Status.OPEN,0);
    }
    private UUID student(UUID account) { return students.create("S"+UUID.randomUUID().toString().replace("-","").substring(0,20),"Event Student",null,account,unit,StudentStatus.ACTIVE).id(); }
    private UserAccount account(RoleCode role) { return accounts.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Event User",passwords.encode("test-only-placeholder"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private Map<String,Object> change(String action,long version) { return Map.of("action",action,"expectedVersion",version); }
    private List<Map<String,Object>> rows() { return jdbc.queryForList("SELECT * FROM event_registrations ORDER BY id"); }
    private long auditCount() { return jdbc.queryForObject("SELECT count(*) FROM event_audit_events",Long.class); }
    private ResultActions call(MockHttpServletRequestBuilder request,Object body,String token) throws Exception { if(!token.isEmpty()) request.header("Authorization","Bearer "+token); if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body)); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() { jdbc.execute("CREATE OR REPLACE FUNCTION reject_event_registration_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$"); jdbc.execute("CREATE TRIGGER reject_event_registration_audit_test BEFORE INSERT ON event_audit_events FOR EACH ROW EXECUTE FUNCTION reject_event_registration_audit_test()"); }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_event_registration_audit_test ON event_audit_events"); }
    private List<Throwable> race(Runnable first,Runnable second) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Throwable>>(); for(var action:List.of(first,second)) futures.add(pool.submit(()->{ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); return catchThrowable(action::run);}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown(); return Arrays.asList(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
}
