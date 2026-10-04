package com.campus.event.infrastructure.persistence;

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

/** Catalog checkpoint: expand registration schema assertions and entity count before the full 5B gate. */
@Testcontainers
class FlywayV23ToV24EventUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor=UUID.randomUUID(),student=UUID.randomUUID(),legacyFee=UUID.randomUUID(),legacyCharge=UUID.randomUUID();
    static Map<String,List<Map<String,Object>>> legacy;
    static List<Map<String,Object>> history;
    @BeforeAll static void upgrade() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        assertThat(flyway("23").migrate().migrationsExecuted).isEqualTo(23);
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

        UUID template=UUID.randomUUID(),notice=UUID.randomUUID();
        jdbc.update("INSERT INTO notification_templates(id,code,name,title,body,row_version) VALUES (?,'NT','Template','Legacy title','Legacy body',2)",template);
        jdbc.update("INSERT INTO notification_notices(id,template_id,title,body,status,published_at,row_version) VALUES (?,?,'Legacy title','Legacy body','PUBLISHED',now(),3)",notice,template);
        jdbc.update("INSERT INTO notification_deliveries(id,notice_id,recipient_id,status,read_at,row_version) VALUES (?,?,?,'READ',now(),1)",UUID.randomUUID(),notice,actor);
        jdbc.update("INSERT INTO notification_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,'NOTICE',?,'PUBLISHED',3,jsonb_build_object('status','PUBLISHED'))",UUID.randomUUID(),actor,notice);
        legacy=snapshot(); history=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("24").migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void preservesAllLegacyDataAndHistoryAndAppliesOnlyV24() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=23 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>23",String.class)).containsExactly("24");
        assertThat(flyway("24").validateWithResult().validationSuccessful).isTrue();
    }
    @Test void verifiesActualCatalogAndAuditSchemaDefaultsConstraintsAndBoundaryWrites() {
        columns("campus_events","id:uuid,code:character varying:32,title:character varying:160,description:character varying:4000,starts_at:timestamp with time zone,ends_at:timestamp with time zone,capacity:integer,status:character varying:16,row_version:bigint,created_at:timestamp with time zone,updated_at:timestamp with time zone",
                Map.of("status","DRAFT","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        columns("event_audit_events","id:uuid,actor_user_id:uuid,resource_type:character varying:16,target_id:uuid,action:character varying:16,resource_version:bigint,occurred_at:timestamp with time zone,metadata:jsonb",
                Map.of("occurred_at","CURRENT_TIMESTAMP","metadata","'{}'::jsonb"));
        for (String table:List.of("campus_events","event_audit_events")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='p'",Integer.class,table)).isEqualTo(1);
        }
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='event_audit_events'::regclass AND contype='f'",String.class))
                .containsExactly("FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='campus_events'::regclass AND contype='f'",Integer.class)).isZero();
        var indexes=jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename IN ('campus_events','event_audit_events') ORDER BY indexname",String.class);
        assertThat(indexes.stream().anyMatch(value -> value.contains("UNIQUE") && value.contains("lower((code)::text)"))).isTrue();
        assertThat(indexes.stream().anyMatch(value -> value.contains("(status, starts_at)"))).isTrue();
        assertThat(indexes.stream().anyMatch(value -> value.contains("(resource_type, target_id, occurred_at)"))).isTrue();
        assertThat(indexes.stream().anyMatch(value -> value.contains("(actor_user_id, occurred_at)"))).isTrue();
        UUID id=event("VALID", "😀".repeat(160),"😀".repeat(4000));
        assertThat(jdbc.queryForMap("SELECT status,row_version FROM campus_events WHERE id=?",id)).containsEntry("status","DRAFT").containsEntry("row_version",0L);
        state("23505",() -> event("valid","Title","Description"));
        state("23505",() -> jdbc.update("INSERT INTO campus_events SELECT * FROM campus_events WHERE id=?",id));
        for(String column:List.of("id","code","title","description","starts_at","ends_at","capacity","status","row_version","created_at","updated_at"))
            state("23502",() -> jdbc.update("UPDATE campus_events SET "+column+"=NULL WHERE id=?",id));
        for(String assignment:List.of("capacity=0","capacity=-1","row_version=-1","starts_at=ends_at","starts_at=ends_at+interval '1 second'","status='UNKNOWN'"))
            state("23514",() -> jdbc.update("UPDATE campus_events SET "+assignment+" WHERE id=?",id));
        for(String column:List.of("code","title","description"))
            for(String invalid:List.of("x"," \t\n\r\u000b\f"," \tx\n"))
                state("23514",() -> jdbc.update("UPDATE campus_events SET "+column+"=? WHERE id=?",invalid,id));
        for(var limit:Map.of("code",32,"title",160,"description",4000).entrySet())
            state("22001",() -> jdbc.update("UPDATE campus_events SET "+limit.getKey()+"=? WHERE id=?","x".repeat(limit.getValue()+1),id));
        jdbc.update("UPDATE campus_events SET capacity=2147483647,status='OPEN' WHERE id=?",id);
        state("22003",() -> jdbc.update("UPDATE campus_events SET capacity=2147483648 WHERE id=?",id));
        UUID audit=UUID.randomUUID();
        jdbc.update("INSERT INTO event_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'EVENT',?,'CREATED',0)",audit,actor,id);
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM event_audit_events WHERE id=?",String.class,audit)).isEqualTo("{}");
        state("23505",() -> jdbc.update("INSERT INTO event_audit_events SELECT * FROM event_audit_events WHERE id=?",audit));
        state("23503",() -> jdbc.update("UPDATE event_audit_events SET actor_user_id=? WHERE id=?",UUID.randomUUID(),audit));
        for(String column:List.of("id","actor_user_id","resource_type","target_id","action","resource_version","occurred_at","metadata"))
            state("23502",() -> jdbc.update("UPDATE event_audit_events SET "+column+"=NULL WHERE id=?",audit));
        for(String assignment:List.of("resource_type='OTHER'","action='READ'","resource_version=-1","metadata='[]'::jsonb","metadata='null'::jsonb"))
            state("23514",() -> jdbc.update("UPDATE event_audit_events SET "+assignment+" WHERE id=?",audit));
        for(String column:List.of("resource_type","action")) state("22001",() -> jdbc.update("UPDATE event_audit_events SET "+column+"=? WHERE id=?","x".repeat(17),audit));
        jdbc.update("UPDATE event_audit_events SET metadata=jsonb_build_object('note',?) WHERE id=?","😀".repeat(5000),audit);
    }
    @Test void validates32ActualProductionEntitiesOnExactUpgradedPublicSchemaWithFlywayDisabled() {
        var before=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username="+postgres.getUsername(),
                        "spring.datasource.password="+postgres.getPassword(),"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).hasSize(32);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().entity(CampusEventEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Test void verifiesRetainedMembershipSchemaUniquenessDefaultsReferencesAndHistory() {
        columns("event_registrations","id:uuid,event_id:uuid,student_id:uuid,status:character varying:16,row_version:bigint,registered_at:timestamp with time zone,cancelled_at:timestamp with time zone,attended_at:timestamp with time zone,created_at:timestamp with time zone,updated_at:timestamp with time zone",
                Map.of("status","REGISTERED","row_version","0","registered_at","CURRENT_TIMESTAMP","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"),Set.of("cancelled_at","attended_at"));
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='event_registrations'::regclass AND contype='p'",String.class)).containsExactly("PRIMARY KEY (id)");
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='event_registrations'::regclass AND contype='f'",String.class))
                .containsExactlyInAnyOrder("FOREIGN KEY (event_id) REFERENCES campus_events(id)","FOREIGN KEY (student_id) REFERENCES students(id)");
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='event_registrations'::regclass AND contype='u'",String.class))
                .containsExactly("UNIQUE (event_id, student_id)");
        var indexes=jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename='event_registrations'",String.class);
        assertThat(indexes.stream().anyMatch(value->value.contains("(event_id, status)"))).isTrue();
        assertThat(indexes.stream().anyMatch(value->value.contains("(student_id, status)"))).isTrue();
        UUID event=event("RG","Registration event","Description"),id=UUID.randomUUID();
        jdbc.update("INSERT INTO event_registrations(id,event_id,student_id) VALUES (?,?,?)",id,event,student);
        var original=jdbc.queryForMap("SELECT * FROM event_registrations WHERE id=?",id);
        assertThat(original).containsEntry("status","REGISTERED").containsEntry("row_version",0L).containsEntry("cancelled_at",null).containsEntry("attended_at",null);
        state("23505",()->jdbc.update("INSERT INTO event_registrations(id,event_id,student_id) VALUES (?,?,?)",UUID.randomUUID(),event,student));
        state("23505",()->jdbc.update("INSERT INTO event_registrations SELECT * FROM event_registrations WHERE id=?",id));
        for(String column:List.of("id","event_id","student_id","status","row_version","registered_at","created_at","updated_at"))
            state("23502",()->jdbc.update("UPDATE event_registrations SET "+column+"=NULL WHERE id=?",id));
        for(String column:List.of("event_id","student_id")) state("23503",()->jdbc.update("UPDATE event_registrations SET "+column+"=? WHERE id=?",UUID.randomUUID(),id));
        for(String change:List.of("row_version=-1","status='UNKNOWN'","status='CANCELLED'","status='ATTENDED'","cancelled_at=now()","attended_at=now()"))
            state("23514",()->jdbc.update("UPDATE event_registrations SET "+change+" WHERE id=?",id));
        state("22001",()->jdbc.update("UPDATE event_registrations SET status=? WHERE id=?","x".repeat(17),id));
        state("23514",()->jdbc.update("UPDATE event_registrations SET status='CANCELLED',cancelled_at=registered_at-interval '1 second' WHERE id=?",id));
        jdbc.update("UPDATE event_registrations SET status='CANCELLED',cancelled_at=registered_at WHERE id=?",id);
        state("23505",()->jdbc.update("INSERT INTO event_registrations(id,event_id,student_id) VALUES (?,?,?)",UUID.randomUUID(),event,student));
        jdbc.update("UPDATE event_registrations SET status='REGISTERED',registered_at=now(),cancelled_at=NULL,row_version=1 WHERE id=?",id);
        assertThat(jdbc.queryForMap("SELECT id,event_id,student_id,created_at FROM event_registrations WHERE id=?",id))
                .containsEntry("id",original.get("id")).containsEntry("event_id",original.get("event_id")).containsEntry("student_id",original.get("student_id")).containsEntry("created_at",original.get("created_at"));
        jdbc.update("UPDATE event_registrations SET status='ATTENDED',attended_at=registered_at WHERE id=?",id);
        state("23514",()->jdbc.update("UPDATE event_registrations SET cancelled_at=now() WHERE id=?",id));
        jdbc.update("INSERT INTO event_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'REGISTRATION',?,'RESTORED',1)",UUID.randomUUID(),actor,id);
        state("23514",()->jdbc.update("INSERT INTO event_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'REGISTRATION',?,'UPDATED',1)",UUID.randomUUID(),actor,id));
    }
    // V24 is historical: later domain entities must not be validated against this schema.
    @Configuration(proxyBeanMethods=false) @EntityScan({
            "com.campus.identity.infrastructure.persistence.entity", "com.campus.organization.infrastructure.persistence",
            "com.campus.student.infrastructure.persistence", "com.campus.personnel.infrastructure.persistence",
            "com.campus.shared.infrastructure.persistence", "com.campus.academic.infrastructure.persistence",
            "com.campus.dormitory.infrastructure.persistence", "com.campus.dormitory.infrastructure.assignment",
            "com.campus.finance.infrastructure.obligations", "com.campus.finance.infrastructure.payments",
            "com.campus.notification.infrastructure.persistence", "com.campus.event.infrastructure.persistence"
    }) static class ValidationConfiguration { }
    private UUID event(String code,String title,String description) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity) VALUES (?,?,?,?,now(),now()+interval '1 hour',1)",id,code,title,description); return id;
    }
    private void columns(String table,String definition,Map<String,String> defaults) {
        columns(table,definition,defaults,Set.of());
    }
    private void columns(String table,String definition,Map<String,String> defaults,Set<String> nullable) {
        var actual=jdbc.queryForList("SELECT column_name,data_type,character_maximum_length,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position",table);
        var expected=definition.split(","); assertThat(actual).hasSize(expected.length);
        for(int i=0;i<expected.length;i++) {
            var field=expected[i].split(":"); var column=actual.get(i);
            assertThat(column.get("column_name")).isEqualTo(field[0]); assertThat(column.get("data_type")).isEqualTo(field[1]);
            assertThat(column.get("is_nullable")).isEqualTo(nullable.contains(field[0])?"YES":"NO");
            assertThat(column.get("character_maximum_length")).isEqualTo(field.length==3?Integer.valueOf(field[2]):null);
            if(defaults.containsKey(field[0])) assertThat(column.get("column_default").toString()).contains(defaults.get(field[0]));
            else assertThat(column.get("column_default")).isNull();
        }
    }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' AND tablename NOT IN ('campus_events','event_registrations','event_audit_events') ORDER BY tablename",String.class))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1")); return result;
    }
    private void state(String expected,Runnable operation) {
        Throwable error=catchThrowable(operation::run); assertThat(error).isNotNull();
        while(error.getCause()!=null) error=error.getCause();
        assertThat(error).isInstanceOf(SQLException.class); assertThat(((SQLException)error).getSQLState()).isEqualTo(expected);
    }
}
