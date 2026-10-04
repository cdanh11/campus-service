package com.campus.notification.infrastructure.persistence;

import java.sql.SQLException;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class FlywayV22ToV23NotificationUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor=UUID.randomUUID(),student=UUID.randomUUID(),legacyFee=UUID.randomUUID(),legacyCharge=UUID.randomUUID();
    static Map<String,List<Map<String,Object>>> legacy;
    static List<Map<String,Object>> history;
    @BeforeAll static void upgrade() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        assertThat(flyway("22").migrate().migrationsExecuted).isEqualTo(22);
        UUID unit=UUID.randomUUID(),faculty=UUID.randomUUID(),course=UUID.randomUUID(),term=UUID.randomUUID(),
                offering=UUID.randomUUID(),section=UUID.randomUUID(),enrollment=UUID.randomUUID(),
                building=UUID.randomUUID(),room=UUID.randomUUID(),bed=UUID.randomUUID(),assignment=UUID.randomUUID();
        jdbc.update("INSERT INTO identity_users (id,email,display_name,password_hash,status,security_version,row_version) VALUES (?,'legacy@campus.example','Legacy User','legacy-hash','ACTIVE',7,3)",actor);
        jdbc.update("INSERT INTO identity_user_roles (user_id,role_id) VALUES (?,'00000000-0000-0000-0000-000000000002')",actor);
        jdbc.update("INSERT INTO organization_units (id,code,name,unit_type,row_version) VALUES (?,'UN','Unit','FACULTY',2)",unit);
        jdbc.update("INSERT INTO students (id,student_number,full_name,organization_unit_id,identity_user_id,row_version) VALUES (?,'ST','Student',?,?,4)",student,unit,actor);
        jdbc.update("INSERT INTO faculty_staff (id,personnel_number,full_name,personnel_type,organization_unit_id) VALUES (?,'FA','Faculty','FACULTY',?)",faculty,unit);
        jdbc.update("INSERT INTO academic_programs (id,code,name,organization_unit_id) VALUES (?,'PR','Program',?)",UUID.randomUUID(),unit);
        jdbc.update("INSERT INTO academic_courses (id,code,title,credits,organization_unit_id) VALUES (?,'CR','Course',3,?)",course,unit);
        jdbc.update("INSERT INTO academic_terms (id,code,name,start_date,end_date,status) VALUES (?,'TE','Term',DATE '2026-01-01',DATE '2026-06-01','ACTIVE')",term);
        jdbc.update("INSERT INTO academic_course_offerings (id,term_id,course_id,organization_unit_id,status) VALUES (?,?,?,?,'OPEN')",offering,term,course,unit);
        jdbc.update("INSERT INTO academic_class_sections (id,offering_id,code,capacity,faculty_id,status) VALUES (?,?,'SC',1,?,'OPEN')",section,offering,faculty);
        jdbc.update("INSERT INTO academic_enrollments (id,student_id,section_id,status,row_version) VALUES (?,?,?,'WITHDRAWN',2)",enrollment,student,section);
        jdbc.update("INSERT INTO people_registry_audit_events (id,actor_user_id,resource_type,target_id,action,occurred_at) VALUES (?,?,'STUDENT',?,'CREATED',now())",UUID.randomUUID(),actor,student);
        jdbc.update("INSERT INTO academic_audit_events (id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,'ENROLLMENT',?,'WITHDRAWN',2,'{\"status\":\"WITHDRAWN\"}')",UUID.randomUUID(),actor,enrollment);
        jdbc.update("INSERT INTO dormitory_buildings (id,code,name,row_version) VALUES (?,'BL','Building',3)",building);
        jdbc.update("INSERT INTO dormitory_rooms (id,building_id,code,name,row_version) VALUES (?,?,'RO','Room',2)",room,building);
        jdbc.update("INSERT INTO dormitory_beds (id,room_id,code,name,row_version) VALUES (?,?,'BE','Bed',1)",bed,room);
        jdbc.update("INSERT INTO dormitory_assignments (id,student_id,bed_id,status,row_version,released_at) VALUES (?,?,?,'RELEASED',2,now())",assignment,student,bed);
        jdbc.update("INSERT INTO dormitory_audit_events (id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,'ASSIGNMENT',?,'RELEASED',2,'{\"status\":\"RELEASED\"}')",UUID.randomUUID(),actor,assignment);
        jdbc.update("INSERT INTO finance_fee_definitions (id,code,name,amount,row_version) VALUES (?,'FE','Legacy Fee',100000,2)",legacyFee);
        jdbc.update("INSERT INTO finance_student_charges (id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date,row_version) VALUES (?,'CH',?,?,'FE','Legacy Fee',100000,DATE '2026-01-01',3)",legacyCharge,student,legacyFee);
        jdbc.update("INSERT INTO finance_audit_events (id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,'CHARGE',?,'CREATED',3,jsonb_build_object('status','OPEN'))",UUID.randomUUID(),actor,legacyCharge);
        jdbc.update("INSERT INTO finance_manual_payments(id,receipt_number,charge_id,amount) VALUES (?,'LEGACY',?,10)",UUID.randomUUID(),legacyCharge);
        legacy=snapshot(); history=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("23").migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void preservesEveryLegacyTableAndAppliesOnlyV23() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=22 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>22",String.class)).containsExactly("23");
        assertThat(flyway("23").validateWithResult().validationSuccessful).isTrue();
    }
    @Test void verifiesAllColumnsDefaultsNullabilityKeysAndIndexes() {
        columns("notification_templates", "id:uuid,code:character varying:32,name:character varying:160,title:character varying:160,body:character varying:4000,status:character varying:16,row_version:bigint,created_at:timestamp with time zone,updated_at:timestamp with time zone",Set.of(),Map.of("status","ACTIVE","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        columns("notification_notices", "id:uuid,template_id:uuid,title:character varying:160,body:character varying:4000,status:character varying:16,row_version:bigint,published_at:timestamp with time zone,created_at:timestamp with time zone,updated_at:timestamp with time zone",Set.of("published_at"),Map.of("status","DRAFT","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        columns("notification_deliveries", "id:uuid,notice_id:uuid,recipient_id:uuid,status:character varying:16,row_version:bigint,delivered_at:timestamp with time zone,read_at:timestamp with time zone,created_at:timestamp with time zone,updated_at:timestamp with time zone",Set.of("read_at"),Map.of("status","UNREAD","row_version","0","delivered_at","CURRENT_TIMESTAMP","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        columns("notification_audit_events", "id:uuid,actor_user_id:uuid,resource_type:character varying:16,target_id:uuid,action:character varying:16,resource_version:bigint,occurred_at:timestamp with time zone,metadata:jsonb",Set.of(),Map.of("occurred_at","CURRENT_TIMESTAMP","metadata","'{}'::jsonb"));
        for(String table:List.of("notification_templates","notification_notices","notification_deliveries","notification_audit_events"))
            assertThat(constraints(table,"p")).containsExactly("PRIMARY KEY (id)");
        assertThat(constraints("notification_notices","f")).containsExactly("FOREIGN KEY (template_id) REFERENCES notification_templates(id)");
        assertThat(constraints("notification_deliveries","f")).containsExactlyInAnyOrder("FOREIGN KEY (notice_id) REFERENCES notification_notices(id)","FOREIGN KEY (recipient_id) REFERENCES identity_users(id)");
        assertThat(constraints("notification_audit_events","f")).containsExactly("FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)");
        assertThat(constraints("notification_deliveries","u")).containsExactly("UNIQUE (notice_id, recipient_id)");
        var indexes=jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND tablename LIKE 'notification_%'",String.class);
        assertThat(indexes).anyMatch(s->s.contains("ux_notification_template_code") && s.contains("lower"))
                .anyMatch(s->s.contains("ix_notification_delivery_recipient_status") && s.contains("(recipient_id, status, delivered_at)"))
                .anyMatch(s->s.contains("ix_notification_notice_status_time") && s.contains("(status, created_at)"))
                .anyMatch(s->s.contains("ix_notification_audit_target_time") && s.contains("(resource_type, target_id, occurred_at)"))
                .anyMatch(s->s.contains("ix_notification_audit_actor_time") && s.contains("(actor_user_id, occurred_at)"));
    }
    @Test void exercisesApprovedConstraintsAndValidBoundariesWithSpecificSqlStates() {
        UUID template=UUID.randomUUID(),notice=UUID.randomUUID(),delivery=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.update("INSERT INTO notification_templates(id,code,name,title,body) VALUES (?,'UP','Name',?,?)",template,"😀".repeat(160),"😀".repeat(4000));
        jdbc.update("INSERT INTO notification_notices(id,template_id,title,body) VALUES (?,?,?,?)",notice,template,"Title","Body");
        jdbc.update("INSERT INTO notification_deliveries(id,notice_id,recipient_id) VALUES (?,?,?)",delivery,notice,actor);
        jdbc.update("INSERT INTO notification_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'NOTICE',?,'CREATED',0)",event,actor,notice);
        for(var entry:Map.of("notification_templates",template,"notification_notices",notice,"notification_deliveries",delivery,"notification_audit_events",event).entrySet()) {
            String table=entry.getKey(); UUID id=entry.getValue();
            for(String column:jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND is_nullable='NO'",String.class,table))
                state("23502",()->jdbc.update("UPDATE "+table+" SET "+column+"=NULL WHERE id=?",id));
            String version=table.equals("notification_audit_events")?"resource_version":"row_version";
            state("23514",()->jdbc.update("UPDATE "+table+" SET "+version+"=-1 WHERE id=?",id));
            assertThat(jdbc.queryForObject("SELECT "+version+" FROM "+table+" WHERE id=?",Long.class,id)).isZero();
        }
        state("23505",()->jdbc.update("INSERT INTO notification_templates(id,code,name,title,body) VALUES (?,'NEW','Name','Title','Body')",template));
        state("23505",()->jdbc.update("INSERT INTO notification_templates(id,code,name,title,body) VALUES (?,'up','Name','Title','Body')",UUID.randomUUID()));
        for(String column:List.of("code","name","title","body")) state("23514",()->jdbc.update("UPDATE notification_templates SET "+column+"=? WHERE id=?"," \t\n\r\u000b\f",template));
        for(var entry:Map.of("code",32,"name",160,"title",160,"body",4000).entrySet()) state("22001",()->jdbc.update("UPDATE notification_templates SET "+entry.getKey()+"=? WHERE id=?","x".repeat(entry.getValue()+1),template));
        state("23514",()->jdbc.update("UPDATE notification_templates SET status='UNKNOWN' WHERE id=?",template));
        state("23503",()->jdbc.update("UPDATE notification_notices SET template_id=? WHERE id=?",UUID.randomUUID(),notice));
        state("23514",()->jdbc.update("UPDATE notification_notices SET status='PUBLISHED' WHERE id=?",notice));
        state("23514",()->jdbc.update("UPDATE notification_notices SET published_at=now() WHERE id=?",notice));
        state("23514",()->jdbc.update("UPDATE notification_notices SET title='x' WHERE id=?",notice));
        state("22001",()->jdbc.update("UPDATE notification_notices SET body=? WHERE id=?","x".repeat(4001),notice));
        jdbc.update("UPDATE notification_notices SET status='PUBLISHED',published_at=now() WHERE id=?",notice);
        state("23505",()->jdbc.update("INSERT INTO notification_deliveries(id,notice_id,recipient_id) VALUES (?,?,?)",UUID.randomUUID(),notice,actor));
        state("23503",()->jdbc.update("UPDATE notification_deliveries SET recipient_id=? WHERE id=?",UUID.randomUUID(),delivery));
        state("23503",()->jdbc.update("UPDATE notification_deliveries SET notice_id=? WHERE id=?",UUID.randomUUID(),delivery));
        state("23514",()->jdbc.update("UPDATE notification_deliveries SET status='READ' WHERE id=?",delivery));
        state("23514",()->jdbc.update("UPDATE notification_deliveries SET status='READ',read_at=delivered_at-INTERVAL '1 second' WHERE id=?",delivery));
        jdbc.update("UPDATE notification_deliveries SET status='READ',read_at=delivered_at WHERE id=?",delivery);
        state("23514",()->jdbc.update("UPDATE notification_deliveries SET status='UNREAD' WHERE id=?",delivery));
        state("23503",()->jdbc.update("UPDATE notification_audit_events SET actor_user_id=? WHERE id=?",UUID.randomUUID(),event));
        state("23514",()->jdbc.update("UPDATE notification_audit_events SET action='READ' WHERE id=?",event));
        state("23514",()->jdbc.update("UPDATE notification_audit_events SET metadata='[]'::jsonb WHERE id=?",event));
        state("22001",()->jdbc.update("UPDATE notification_audit_events SET resource_type=? WHERE id=?","x".repeat(17),event));
        jdbc.update("UPDATE notification_audit_events SET metadata=jsonb_build_object('large',?) WHERE id=?","😀".repeat(5000),event);
        jdbc.update("UPDATE notification_audit_events SET resource_type='DELIVERY',action='READ' WHERE id=?",event);
        state("23514",()->jdbc.update("UPDATE notification_audit_events SET action='CREATED' WHERE id=?",event));
        assertThat(snapshot()).isEqualTo(legacy);
    }
    private void columns(String table,String specification,Set<String> nullable,Map<String,String> defaults) {
        var columns=new LinkedHashMap<String,Map<String,Object>>();
        for(var c:jdbc.queryForList("SELECT column_name,data_type,character_maximum_length,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name=?",table)) columns.put(c.get("column_name").toString(),c);
        var names=new ArrayList<String>();
        for(String item:specification.split(",")) {
            var parts=item.split(":"); names.add(parts[0]); var column=columns.get(parts[0]);
            assertThat(column).isNotNull().containsEntry("data_type",parts[1]).containsEntry("is_nullable",nullable.contains(parts[0])?"YES":"NO");
            if(parts.length==3) assertThat(column.get("character_maximum_length")).isEqualTo(Integer.valueOf(parts[2]));
            else assertThat(column.get("character_maximum_length")).isNull();
            if(defaults.containsKey(parts[0])) assertThat(column.get("column_default").toString()).contains(defaults.get(parts[0]));
            else assertThat(column.get("column_default")).isNull();
        }
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(names);
    }
    private List<String> constraints(String table,String type) {
        return jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid=CAST(? AS regclass) AND contype=CAST(? AS char)",String.class,table,type);
    }
    @Test void validatesAll29ProductionEntitiesOnExactV23SchemaWithFlywayDisabled() {
        var before=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username="+postgres.getUsername(),
                        "spring.datasource.password="+postgres.getPassword(),"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).hasSize(29);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().entity(NotificationDeliveryEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Configuration(proxyBeanMethods=false) @EntityScan("com.campus") static class ValidationConfiguration { }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' AND tablename NOT LIKE 'notification_%' ORDER BY tablename",String.class))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1")); return result;
    }
    private void state(String expected,Runnable task) {
        Throwable failure=catchThrowable(task::run); assertThat(failure).isNotNull();
        while(failure.getCause()!=null) failure=failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class); assertThat(((SQLException)failure).getSQLState()).isEqualTo(expected);
    }
}
