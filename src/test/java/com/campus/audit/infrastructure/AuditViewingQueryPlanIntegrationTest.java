package com.campus.audit.infrastructure;

import java.util.*;
import com.campus.audit.application.AuditViewingService;
import com.campus.shared.application.audit.*;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Existing selective target indexes, not an unfiltered global-page performance claim. */
@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class AuditViewingQueryPlanIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AuditViewingService audits;
    @Test void everyOwnerSelectiveTargetPageUsesExistingIndexAndMatchesProductionProjection() throws Exception {
        UUID actor=UUID.randomUUID(),other=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users(id,email,display_name,password_hash,status) VALUES (?,?,'Query User','test-only-hash','ACTIVE'),(?,?,'Other User','test-only-hash','ACTIVE')",actor,actor+"@campus.example",other,other+"@campus.example");
        var definitions=List.of(
                new Definition(AuditSource.IDENTITY,"identity_admin_audit_events","USER","target_user_id","ix_identity_admin_audit_target_occurred"),
                new Definition(AuditSource.PEOPLE,"people_registry_audit_events","STUDENT","target_id","ix_people_registry_audit_target_occurred"),
                new Definition(AuditSource.ACADEMIC,"academic_audit_events","PROGRAM","target_id","ix_academic_audit_resource_time"),
                new Definition(AuditSource.DORMITORY,"dormitory_audit_events","BUILDING","target_id","ix_dormitory_audit_resource_time"),
                new Definition(AuditSource.FINANCE,"finance_audit_events","FEE","target_id","ix_finance_audit_target_time"),
                new Definition(AuditSource.NOTIFICATION,"notification_audit_events","TEMPLATE","target_id","ix_notification_audit_target_time"),
                new Definition(AuditSource.EVENT,"event_audit_events","EVENT","target_id","ix_event_audit_target_time"),
                new Definition(AuditSource.LIBRARY,"library_audit_events","TITLE","target_id","ix_library_audit_target_time"));
        for(var definition:definitions) {
            boolean identity=definition.source()==AuditSource.IDENTITY,people=definition.source()==AuditSource.PEOPLE;
            UUID target=identity?actor:UUID.randomUUID();
            String columns="id,actor_user_id,"+definition.targetColumn()+",action,occurred_at";
            String values="gen_random_uuid(),CASE WHEN i<=10 THEN ?::uuid ELSE ?::uuid END,CASE WHEN i<=10 THEN ?::uuid ELSE "+(identity?"?::uuid":"gen_random_uuid()")+" END,'"+(identity?"USER_CREATED":"CREATED")+"',TIMESTAMPTZ '2026-01-01 00:00:00Z'+i*interval '1 second'";
            var arguments=new ArrayList<Object>(List.of(actor,other,target)); if(identity) arguments.add(other);
            if(!identity) { columns+=",resource_type"; values+=",'"+definition.resource()+"'"; }
            if(!identity&&!people) { columns+=",resource_version,metadata"; values+=",7,jsonb_build_object('status','"+(definition.source()==AuditSource.EVENT?"DRAFT":"ACTIVE")+"')"; }
            jdbc.update("INSERT INTO "+definition.table()+"("+columns+") SELECT "+values+" FROM generate_series(1,10000) i",arguments.toArray());
            jdbc.execute("ANALYZE "+definition.table());
            String predicate=definition.targetColumn()+"=?"+(!identity&&!people?" AND resource_type='"+definition.resource()+"'":"");
            var plan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT id FROM "+definition.table()+" WHERE "+predicate+" ORDER BY occurred_at,id LIMIT 5",String.class,target));
            assertThat(plan.findValuesAsText("Index Name")).as(definition.source().name()).contains(definition.index());
            var expected=jdbc.queryForList("SELECT id FROM "+definition.table()+" WHERE "+predicate+" ORDER BY occurred_at,id LIMIT 5",UUID.class,target);
            var result=audits.search(definition.source(),new AuditSearch(0,5,target,actor,definition.resource(),identity?"USER_CREATED":"CREATED",null,null,true));
            assertThat(result.totalElements()).isEqualTo(10); assertThat(result.content().stream().map(AuditView::id).toList()).isEqualTo(expected);
            assertThat(result.content()).allSatisfy(event->{
                assertThat(event.source()).isEqualTo(definition.source()); assertThat(event.actorId()).isEqualTo(actor);
                if(identity||people) { assertThat(event.resourceVersion()).isNull(); assertThat(event.metadata()).isEmpty(); }
                else assertThat(event.resourceVersion()).isEqualTo(7L);
            });
        }
    }
    private record Definition(AuditSource source,String table,String resource,String targetColumn,String index) { }
}
