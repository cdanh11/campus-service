package com.campus.notification.api;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.notification.application.NotificationService;
import com.campus.notification.domain.*;
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
class NotificationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired NotificationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired NotificationRepository repository;
    @Autowired PlatformTransactionManager transactions;
    UUID actor,recipient,other; String admin,user,stranger,prefix;
    NotificationTemplate template; Notice notice;
    static final String ROOT="/api/v1/admin/notifications";
    static final String INBOX="/api/v1/notifications";
    @BeforeEach void setup() {
        prefix=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        var account=account(RoleCode.ADMIN,AccountStatus.ACTIVE); actor=account.id(); admin=tokens.accessToken(account);
        account=account(RoleCode.USER,AccountStatus.ACTIVE); recipient=account.id(); user=tokens.accessToken(account);
        account=account(RoleCode.USER,AccountStatus.ACTIVE); other=account.id(); stranger=tokens.accessToken(account);
        template=service.createTemplate(actor,prefix,"Test template","Original title","Original body");
        notice=service.createNotice(actor,template.id());
    }
    @Test void protectsAllAdminOperationsAndRequiresAuthenticationForInbox() throws Exception {
        var before=rows(); long events=events();
        for (String token:List.of("",user)) for(var request:List.of(get(ROOT+"/templates"),post(ROOT+"/templates"),get(ROOT+"/templates/"+template.id()),
                put(ROOT+"/templates/"+template.id()),get(ROOT+"/notices"),post(ROOT+"/notices"),get(ROOT+"/notices/"+notice.id()),put(ROOT+"/notices/"+notice.id()),post(ROOT+"/notices/"+notice.id()+"/publish")))
            call(request,Map.of(),token).andExpect(status().is(token.isEmpty()?401:403));
        for(var request:List.of(get(INBOX),get(INBOX+"/"+UUID.randomUUID()),put(INBOX+"/"+UUID.randomUUID()+"/read"))) call(request,Map.of(),"").andExpect(status().isUnauthorized());
        assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(events);
    }
    @Test void templateSnapshotPublishAndOwnedInboxRetainStoredHistory() throws Exception {
        var changed=service.updateTemplate(actor,template.id(),prefix,"Changed","Changed title","Changed body",NotificationTemplate.Status.INACTIVE,0);
        assertThat(changed.rowVersion()).isEqualTo(1);
        assertThat(service.notice(notice.id()).title()).isEqualTo("Original title");
        var published=read(call(post(ROOT+"/notices/"+notice.id()+"/publish"),Map.of("recipientIds",List.of(recipient,other),"expectedVersion",0),admin).andExpect(status().isOk()));
        assertThat(published.get("rowVersion").asLong()).isEqualTo(1);
        assertThat(read(call(get(ROOT+"/notices/"+notice.id()),null,admin))).isEqualTo(published);
        call(get(INBOX),null,user).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Original title")).andExpect(jsonPath("$.content[0].body").value("Original body"));
        UUID delivery=delivery(recipient);
        call(get(INBOX+"/"+delivery),null,stranger).andExpect(status().isNotFound());
        call(put(INBOX+"/"+delivery+"/read"),Map.of("expectedVersion",0),stranger).andExpect(status().isNotFound());
        call(put(INBOX+"/"+delivery+"/read"),Map.of("expectedVersion",9),user).andExpect(status().isConflict());
        long before=events();
        var acknowledged=read(call(put(INBOX+"/"+delivery+"/read"),Map.of("expectedVersion",0,"recipientId",other),user).andExpect(status().isOk()));
        assertThat(acknowledged.get("rowVersion").asLong()).isEqualTo(1);
        assertThat(acknowledged.get("recipientId").asText()).isEqualTo(recipient.toString());
        assertThat(read(call(get(INBOX+"/"+delivery),null,user)).get("delivery")).isEqualTo(acknowledged);
        assertThat(read(call(put(INBOX+"/"+delivery+"/read"),Map.of("expectedVersion",0),user))).isEqualTo(acknowledged);
        assertThat(events()).isEqualTo(before+1);
        call(put(ROOT+"/notices/"+notice.id()),Map.of("title","Changed","body","Changed","expectedVersion",1),admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_STATE"));
        call(post(ROOT+"/notices/"+notice.id()+"/publish"),Map.of("recipientIds",List.of(recipient),"expectedVersion",0),admin).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE notice_id=?",Long.class,notice.id())).isEqualTo(2);
    }
    @Test void rejectsInvalidBatchesInactiveRecipientsAndUnavailableTemplatesAtomically() throws Exception {
        var inactive=account(RoleCode.USER,AccountStatus.DISABLED); var before=rows(); long count=events();
        for(var recipients:List.of(List.of(recipient,recipient),List.<UUID>of(),Collections.nCopies(101,recipient)))
            call(post(ROOT+"/notices/"+notice.id()+"/publish"),Map.of("recipientIds",recipients,"expectedVersion",0),admin).andExpect(status().isBadRequest());
        for(UUID invalid:List.of(UUID.randomUUID(),inactive.id()))
            call(post(ROOT+"/notices/"+notice.id()+"/publish"),Map.of("recipientIds",List.of(recipient,invalid),"expectedVersion",0),admin).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        service.updateTemplate(actor,template.id(),prefix,"Template","Title","Body",NotificationTemplate.Status.INACTIVE,0);
        call(post(ROOT+"/notices"),Map.of("templateId",template.id()),admin).andExpect(status().isConflict());
        call(post(ROOT+"/notices"),Map.of("templateId",UUID.randomUUID()),admin).andExpect(status().isNotFound());
    }
    @Test void realAuditFailureRollsBackPublishAndAcknowledgement() throws Exception {
        var before=rows(); long count=events(); rejectAudit();
        try {
            call(post(ROOT+"/notices/"+notice.id()+"/publish"),Map.of("recipientIds",List.of(recipient,other),"expectedVersion",0),admin)
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("AUDIT_WRITE_FAILED"));
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        } finally { allowAudit(); }
        service.publish(actor,notice.id(),List.of(recipient),0); before=rows(); count=events(); rejectAudit();
        try {
            call(put(INBOX+"/"+delivery(recipient)+"/read"),Map.of("expectedVersion",0),user).andExpect(status().isInternalServerError());
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        } finally { allowAudit(); }
    }
    @Test void duplicateAndStaleTemplateWritesPreserveRowsAndEvents() throws Exception {
        var before=rows(); long count=events();
        call(post(ROOT+"/templates"),Map.of("code",prefix.toLowerCase(Locale.ROOT),"name","Name","title","Title","body","Body"),admin).andExpect(status().isConflict());
        call(put(ROOT+"/templates/"+template.id()),Map.of("code",prefix,"name","Name","title","Title","body","Body","status","ACTIVE","expectedVersion",9),admin).andExpect(status().isConflict());
        assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        var created=read(call(post(ROOT+"/templates"),Map.of("code",prefix+"NEW","name","Name","title","Title","body","Body","actorUserId",other),admin).andExpect(status().isCreated()));
        assertThat(read(call(get(ROOT+"/templates/"+created.get("id").asText()),null,admin))).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM notification_audit_events WHERE target_id=?",UUID.class,UUID.fromString(created.get("id").asText()))).isEqualTo(actor);
    }
    @Test void auditFailureRollsBackEveryDraftAndTemplateMutation() {
        var before=rows(); long count=events(); rejectAudit();
        try {
            assertThatThrownBy(()->service.createTemplate(actor,prefix+"FAIL","Name","Title","Body")).isInstanceOf(com.campus.notification.application.NotificationAudit.UnavailableException.class);
            assertThatThrownBy(()->service.updateTemplate(actor,template.id(),prefix,"Changed","Changed","Changed",NotificationTemplate.Status.INACTIVE,0)).isInstanceOf(com.campus.notification.application.NotificationAudit.UnavailableException.class);
            assertThatThrownBy(()->service.createNotice(actor,template.id())).isInstanceOf(com.campus.notification.application.NotificationAudit.UnavailableException.class);
            assertThatThrownBy(()->service.editNotice(actor,notice.id(),"Changed","Changed",0)).isInstanceOf(com.campus.notification.application.NotificationAudit.UnavailableException.class);
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        } finally { allowAudit(); }
    }
    @Test void activeRecipientCheckSeesCommittedDeactivationEvenWithCachedIdentity() throws Exception {
        var pool=Executors.newSingleThreadExecutor(); var before=rows(); long count=events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(transaction->{
                assertThat(users.findById(recipient).orElseThrow().status()).isEqualTo(AccountStatus.ACTIVE);
                var update=pool.submit(()->jdbc.update("UPDATE identity_users SET status='DISABLED' WHERE id=?",recipient));
                try { assertThat(update.get(10,TimeUnit.SECONDS)).isEqualTo(1); } catch(Exception failure) { throw new IllegalStateException(failure); }
                assertThatThrownBy(()->service.publish(actor,notice.id(),List.of(recipient),0)).isInstanceOf(NotificationService.ReferenceUnavailableException.class);
                transaction.setRollbackOnly();
            });
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void productionNoticeLockBlocksPublishThenSucceedsAfterRelease() throws Exception {
        var pool=Executors.newSingleThreadExecutor(); var before=rows(); long count=events();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(holder->{
                repository.lockNotice(notice.id());
                var blocked=pool.submit(()->catchThrowable(()->new TransactionTemplate(transactions).executeWithoutResult(contender->{
                    jdbc.execute("SET LOCAL lock_timeout='500ms'"); service.publish(actor,notice.id(),List.of(recipient),0);
                })));
                try {
                    Throwable failure=blocked.get(10,TimeUnit.SECONDS); assertThat(failure).isNotNull();
                    while(failure.getCause()!=null) failure=failure.getCause();
                    assertThat(failure).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException)failure).getSQLState()).isEqualTo("55P03");
                } catch(Exception failure) { throw new IllegalStateException(failure); }
            });
            assertThat(rows()).isEqualTo(before); assertThat(events()).isEqualTo(count);
            assertThat(service.publish(actor,notice.id(),List.of(recipient),0).status()).isEqualTo(Notice.Status.PUBLISHED);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void competingPublishesCommitOneBatchAndOneEvent() throws Exception {
        long before=events(); var failures=race(() -> service.publish(actor,notice.id(),List.of(recipient),0),()->service.publish(actor,notice.id(),List.of(other),0));
        assertThat(failures).filteredOn(Objects::isNull).hasSize(1);
        assertThat(failures).filteredOn(Objects::nonNull).singleElement().isInstanceOf(NotificationService.StaleVersionException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE notice_id=?",Long.class,notice.id())).isEqualTo(1);
        assertThat(events()).isEqualTo(before+1);
    }
    @Test void competingReadAcknowledgementsAreIdempotent() throws Exception {
        service.publish(actor,notice.id(),List.of(recipient),0); UUID id=delivery(recipient); long before=events();
        assertThat(race(()->service.markRead(recipient,id,0),()->service.markRead(recipient,id,0))).containsOnlyNulls();
        assertThat(service.delivery(recipient,id).delivery().rowVersion()).isEqualTo(1); assertThat(events()).isEqualTo(before+1);
    }
    @Test void queryBoundsLiteralSearchAndOpenApiAreConsistent() throws Exception {
        service.createTemplate(actor,prefix+"LIT","Literal %_!😀","Title","Body");
        call(get(ROOT+"/templates").param("q","%_!😀"),null,admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for(var invalid:List.of(Map.entry("page","-1"),Map.entry("page","2147483647"),Map.entry("size","101"),Map.entry("sort","id,asc"),Map.entry("status","UNKNOWN"),Map.entry("q","x".repeat(101))))
            call(get(ROOT+"/templates").param(invalid.getKey(),invalid.getValue()),null,admin).andExpect(status().isBadRequest());
        for(String field:List.of("code","name","status","createdAt","updatedAt")) for(String direction:List.of("asc","desc"))
            call(get(ROOT+"/templates").param("sort",field+","+direction),null,admin).andExpect(status().isOk());
        var second=service.createNotice(actor,template.id());
        for(var draft:List.of(notice,second)) {
            service.editNotice(actor,draft.id(),prefix+" %_!😀","Body",0);
            service.publish(actor,draft.id(),List.of(recipient),1);
        }
        for(String field:List.of("title","status","createdAt","updatedAt")) for(String direction:List.of("asc","desc"))
            call(get(ROOT+"/notices").param("q",prefix+" %_!😀").param("sort",field+","+direction),null,admin)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        jdbc.update("UPDATE notification_deliveries SET delivered_at=TIMESTAMPTZ '2026-01-01 00:00:00+00' WHERE recipient_id=?",recipient);
        var ids=jdbc.queryForList("SELECT id FROM notification_deliveries WHERE recipient_id=? ORDER BY id",UUID.class,recipient);
        for(int page=0;page<2;page++) call(get(INBOX).param("sort","deliveredAt,desc").param("size","1").param("page",Integer.toString(page)),null,user)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].delivery.id").value(ids.get(page).toString())).andExpect(jsonPath("$.totalElements").value(2));
        for(String field:List.of("status","deliveredAt","createdAt","updatedAt")) for(String direction:List.of("asc","desc"))
            call(get(INBOX).param("sort",field+","+direction).param("status","UNREAD"),null,user).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        call(get(INBOX).param("sort","recipientId,asc"),null,user).andExpect(status().isBadRequest());
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String path:List.of(ROOT+"/templates",ROOT+"/templates/{id}",ROOT+"/notices",ROOT+"/notices/{id}",ROOT+"/notices/{id}/publish",INBOX,INBOX+"/{id}",INBOX+"/{id}/read"))
            spec.path("paths").path(path).elements().forEachRemaining(operation -> assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue());
        assertThat(spec.path("components").path("schemas").path("NotificationNoticePublish").path("required").toString()).contains("recipientIds","expectedVersion");
    }
    private UserAccount account(RoleCode role,AccountStatus status) { return users.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Notification User",passwords.encode("valid-password"),status,Set.of(roles.findByCode(role).orElseThrow()),Instant.now())); }
    private UUID delivery(UUID recipient) { return jdbc.queryForObject("SELECT id FROM notification_deliveries WHERE notice_id=? AND recipient_id=?",UUID.class,notice.id(),recipient); }
    private long events() { return jdbc.queryForObject("SELECT count(*) FROM notification_audit_events",Long.class); }
    private Map<String,List<Map<String,Object>>> rows() { var result=new LinkedHashMap<String,List<Map<String,Object>>>(); for(String table:List.of("notification_templates","notification_notices","notification_deliveries")) result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id")); return result; }
    private ResultActions call(MockHttpServletRequestBuilder request,Object body,String token) throws Exception { if(!token.isEmpty()) request.header("Authorization","Bearer "+token); request.contentType("application/json"); if(body!=null) request.content(json.writeValueAsString(body)); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    private void rejectAudit() { jdbc.execute("CREATE OR REPLACE FUNCTION reject_notification_audit_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced audit failure'; END $$"); jdbc.execute("CREATE TRIGGER reject_notification_audit_test BEFORE INSERT ON notification_audit_events FOR EACH ROW EXECUTE FUNCTION reject_notification_audit_test()"); }
    private void allowAudit() { jdbc.execute("DROP TRIGGER IF EXISTS reject_notification_audit_test ON notification_audit_events"); }
    private List<Throwable> race(Runnable first,Runnable second) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Throwable>>(); for(var action:List.of(first,second)) futures.add(pool.submit(()->{ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout"); return catchThrowable(action::run);}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown(); return Arrays.asList(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
}
