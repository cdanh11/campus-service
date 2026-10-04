package com.campus.library.infrastructure.persistence;

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
class FlywayV24ToV25LibraryUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor=UUID.randomUUID(),student=UUID.randomUUID(),legacyFee=UUID.randomUUID(),legacyCharge=UUID.randomUUID();
    static Map<String,List<Map<String,Object>>> legacy;
    static List<Map<String,Object>> history;
    @BeforeAll static void upgrade() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        assertThat(flyway("24").migrate().migrationsExecuted).isEqualTo(24);
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

        UUID event=UUID.randomUUID(),registration=UUID.randomUUID();
        jdbc.update("INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity,status,row_version) VALUES (?,'EV','Legacy event','Description',now(),now()+interval '1 hour',2,'OPEN',3)",event);
        jdbc.update("INSERT INTO event_registrations(id,event_id,student_id,status,cancelled_at,row_version) VALUES (?,?,?,'CANCELLED',now(),2)",registration,event,student);
        jdbc.update("INSERT INTO event_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,'REGISTRATION',?,'CANCELLED',2,jsonb_build_object('status','CANCELLED'))",UUID.randomUUID(),actor,registration);
        legacy=snapshot(); history=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("25").migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void preservesPopulatedPreviousModulesAndMigrationHistoryApplyingExactlyV25() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=24 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>24",String.class)).containsExactly("25");
        assertThat(flyway("25").validateWithResult().validationSuccessful).isTrue();
    }
    @Test void validatesAllProductionEntitiesAgainstExactUpgradedSchemaWithoutFlywayOrSchemaCreation() {
        var before=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username="+postgres.getUsername(),
                        "spring.datasource.password="+postgres.getPassword(),"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.properties.hibernate.default_schema=public","spring.jpa.open-in-view=false")
                .run(context->{
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var factory=context.getBean(EntityManagerFactory.class);
                    assertThat(factory.getMetamodel().getEntities()).hasSize(36);
                    assertThat(factory.getProperties().get("hibernate.hbm2ddl.auto")).isEqualTo("validate");
                    assertThat(factory.getMetamodel().getEntities().stream().map(entity->entity.getJavaType().getPackageName()).distinct().toList())
                            .contains("com.campus.library.infrastructure.persistence","com.campus.event.infrastructure.persistence");
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Test void verifiesCatalogColumnContractsDefaultsForeignKeysUniqueAndWhitespaceChecks() {
        columns("library_titles","id:uuid,code:character varying:32,title:character varying:160,author:character varying:160,status:character varying:16,row_version:bigint,created_at:timestamp with time zone,updated_at:timestamp with time zone",
                Map.of("status","ACTIVE","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"),Set.of());
        columns("library_copies","id:uuid,title_id:uuid,code:character varying:32,status:character varying:16,row_version:bigint,created_at:timestamp with time zone,updated_at:timestamp with time zone",
                Map.of("status","ACTIVE","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"),Set.of());
        assertThat(foreignKeys("library_titles")).isEmpty();
        assertThat(foreignKeys("library_copies")).containsExactly("FOREIGN KEY (title_id) REFERENCES library_titles(id)");
        UUID title=title("SC"),copy=copy(title,"CP");
        for(String table:List.of("library_titles","library_copies")) {
            UUID id=table.equals("library_titles")?title:copy;
            assertThat(jdbc.queryForMap("SELECT status,row_version,created_at,updated_at FROM "+table+" WHERE id=?",id))
                    .containsEntry("status","ACTIVE").containsEntry("row_version",0L).doesNotContainValue(null);
            primary(table); state("23505",()->jdbc.update("INSERT INTO "+table+" SELECT * FROM "+table+" WHERE id=?",id));
            for(String column:table.equals("library_titles")?List.of("id","code","title","author","status","row_version","created_at","updated_at")
                    :List.of("id","title_id","code","status","row_version","created_at","updated_at"))
                state("23502",()->jdbc.update("UPDATE "+table+" SET "+column+"=NULL WHERE id=?",id));
            for(String change:List.of("status='UNKNOWN'","row_version=-1")) state("23514",()->jdbc.update("UPDATE "+table+" SET "+change+" WHERE id=?",id));
            state("22001",()->jdbc.update("UPDATE "+table+" SET status=? WHERE id=?","x".repeat(17),id));
            for(String column:table.equals("library_titles")?List.of("code","title","author"):List.of("code")) {
                int max=column.equals("code")?32:160;
                for(String invalid:List.of("a"," a "," \t\n\r\u000b\f ")) state("23514",()->jdbc.update("UPDATE "+table+" SET "+column+"=? WHERE id=?",invalid,id));
                state("22001",()->jdbc.update("UPDATE "+table+" SET "+column+"=? WHERE id=?","😀".repeat(max+1),id));
                jdbc.update("UPDATE "+table+" SET "+column+"=? WHERE id=?","😀".repeat(max),id);
                jdbc.update("UPDATE "+table+" SET "+column+"='vv' WHERE id=?",id);
            }
        }
        state("23505",()->title("VV"));
        state("23505",()->copy(title,"VV"));
        state("23503",()->jdbc.update("UPDATE library_copies SET title_id=? WHERE id=?",UUID.randomUUID(),copy));
        state("23503",()->jdbc.update("DELETE FROM library_titles WHERE id=?",title));
        var indexes=indexes();
        assertThat(indexes.stream().filter(value->value.contains("UNIQUE")&&value.contains("lower((code)::text)")).count()).isEqualTo(2);
        assertThat(indexes.stream().anyMatch(value->value.contains("(status, code)"))).isTrue();
        assertThat(indexes.stream().anyMatch(value->value.contains("(title_id, status)"))).isTrue();
    }
    @Test void verifiesLoanDefaultsHistoryChecksPartialUniqueAndValidSubsequentLoan() {
        columns("library_loans","id:uuid,copy_id:uuid,student_id:uuid,status:character varying:16,row_version:bigint,borrowed_at:timestamp with time zone,due_at:timestamp with time zone,returned_at:timestamp with time zone,created_at:timestamp with time zone,updated_at:timestamp with time zone",
                Map.of("status","OPEN","row_version","0","borrowed_at","CURRENT_TIMESTAMP","due_at","14 days","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"),Set.of("returned_at"));
        primary("library_loans");
        assertThat(foreignKeys("library_loans")).containsExactlyInAnyOrder("FOREIGN KEY (copy_id) REFERENCES library_copies(id)","FOREIGN KEY (student_id) REFERENCES students(id)");
        UUID copy=copy(title("LN"),"LC"),id=loan(copy);
        var original=jdbc.queryForMap("SELECT * FROM library_loans WHERE id=?",id);
        assertThat(original).containsEntry("status","OPEN").containsEntry("row_version",0L).containsEntry("returned_at",null);
        assertThat(jdbc.queryForObject("SELECT due_at-borrowed_at=interval '14 days' FROM library_loans WHERE id=?",Boolean.class,id)).isTrue();
        state("23505",()->loan(copy));
        state("23505",()->jdbc.update("INSERT INTO library_loans SELECT * FROM library_loans WHERE id=?",id));
        for(String column:List.of("id","copy_id","student_id","status","row_version","borrowed_at","due_at","created_at","updated_at"))
            state("23502",()->jdbc.update("UPDATE library_loans SET "+column+"=NULL WHERE id=?",id));
        for(String column:List.of("copy_id","student_id")) state("23503",()->jdbc.update("UPDATE library_loans SET "+column+"=? WHERE id=?",UUID.randomUUID(),id));
        for(String change:List.of("status='UNKNOWN'","row_version=-1","due_at=borrowed_at","due_at=borrowed_at-interval '1 second'","returned_at=now()","status='RETURNED'"))
            state("23514",()->jdbc.update("UPDATE library_loans SET "+change+" WHERE id=?",id));
        state("22001",()->jdbc.update("UPDATE library_loans SET status=? WHERE id=?","x".repeat(17),id));
        state("23514",()->jdbc.update("UPDATE library_loans SET status='RETURNED',returned_at=borrowed_at-interval '1 second' WHERE id=?",id));
        jdbc.update("UPDATE library_loans SET status='RETURNED',returned_at=borrowed_at,row_version=1 WHERE id=?",id);
        assertThat(jdbc.queryForMap("SELECT id,copy_id,student_id,borrowed_at,due_at,created_at FROM library_loans WHERE id=?",id))
                .containsAllEntriesOf(original.entrySet().stream().filter(entry->Set.of("id","copy_id","student_id","borrowed_at","due_at","created_at").contains(entry.getKey()))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)));
        UUID subsequent=loan(copy); assertThat(subsequent).isNotEqualTo(id);
        state("23514",()->jdbc.update("UPDATE library_loans SET returned_at=NULL WHERE id=?",id));
        state("23503",()->jdbc.update("DELETE FROM library_copies WHERE id=?",copy));
        var indexes=indexes();
        assertThat(indexes.stream().anyMatch(value->value.contains("ux_library_open_copy")&&value.contains("UNIQUE")&&value.contains("(copy_id)")&&value.contains("WHERE")&&value.contains("OPEN"))).isTrue();
        for(String columns:List.of("(student_id, status)","(copy_id, borrowed_at)","(status, due_at)")) assertThat(indexes.stream().anyMatch(value->value.contains(columns))).isTrue();
    }
    @Test void verifiesAuditColumnsPolicyMetadataDefaultsNullabilityAndNoInventedJsonLength() {
        columns("library_audit_events","id:uuid,actor_user_id:uuid,resource_type:character varying:16,target_id:uuid,action:character varying:16,resource_version:bigint,occurred_at:timestamp with time zone,metadata:jsonb",
                Map.of("occurred_at","CURRENT_TIMESTAMP","metadata","'{}'::jsonb"),Set.of());
        primary("library_audit_events");
        assertThat(foreignKeys("library_audit_events")).containsExactly("FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO library_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'TITLE',?,'CREATED',0)",id,actor,UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM library_audit_events WHERE id=?",String.class,id)).isEqualTo("{}");
        state("23505",()->jdbc.update("INSERT INTO library_audit_events SELECT * FROM library_audit_events WHERE id=?",id));
        for(String column:List.of("id","actor_user_id","resource_type","target_id","action","resource_version","occurred_at","metadata"))
            state("23502",()->jdbc.update("UPDATE library_audit_events SET "+column+"=NULL WHERE id=?",id));
        state("23503",()->jdbc.update("UPDATE library_audit_events SET actor_user_id=? WHERE id=?",UUID.randomUUID(),id));
        for(String column:List.of("resource_type","action")) state("22001",()->jdbc.update("UPDATE library_audit_events SET "+column+"=? WHERE id=?","x".repeat(17),id));
        for(String change:List.of("resource_version=-1","resource_type='UNKNOWN'","action='BORROWED'","metadata='[]'::jsonb","metadata='null'::jsonb","metadata='1'::jsonb"))
            state("23514",()->jdbc.update("UPDATE library_audit_events SET "+change+" WHERE id=?",id));
        for(String resource:List.of("TITLE","COPY","LOAN")) for(String action:resource.equals("LOAN")?List.of("BORROWED","RETURNED"):List.of("CREATED","UPDATED"))
            jdbc.update("INSERT INTO library_audit_events(id,actor_user_id,resource_type,target_id,action,resource_version,metadata) VALUES (?,?,?,?,?,1,jsonb_build_object('status',?))",UUID.randomUUID(),actor,resource,UUID.randomUUID(),action,"OPEN");
        jdbc.update("UPDATE library_audit_events SET metadata=jsonb_build_object('large',?) WHERE id=?","😀".repeat(10000),id);
        assertThat(jdbc.queryForObject("SELECT char_length(metadata->>'large') FROM library_audit_events WHERE id=?",Integer.class,id)).isEqualTo(10000);
        assertThat(indexes().stream().anyMatch(value->value.contains("(resource_type, target_id, occurred_at)"))).isTrue();
        assertThat(indexes().stream().anyMatch(value->value.contains("(actor_user_id, occurred_at)"))).isTrue();
    }
    @Configuration(proxyBeanMethods=false) @EntityScan("com.campus") static class ValidationConfiguration { }
    private UUID title(String code) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO library_titles(id,code,title,author) VALUES (?,?,'Title','Author')",id,code); return id;
    }
    private UUID copy(UUID title,String code) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO library_copies(id,title_id,code) VALUES (?,?,?)",id,title,code); return id;
    }
    private UUID loan(UUID copy) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO library_loans(id,copy_id,student_id) VALUES (?,?,?)",id,copy,student); return id;
    }
    private void primary(String table) { assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid=?::regclass AND contype='p'",Integer.class,table)).isEqualTo(1); }
    private List<String> foreignKeys(String table) { return jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid=?::regclass AND contype='f'",String.class,table); }
    private List<String> indexes() { return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename LIKE 'library_%'",String.class); }
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
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' AND tablename NOT LIKE 'library_%' ORDER BY tablename",String.class))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1")); return result;
    }
    private void state(String expected,Runnable operation) {
        Throwable error=catchThrowable(operation::run); assertThat(error).isNotNull();
        while(error.getCause()!=null) error=error.getCause();
        assertThat(error).isInstanceOf(SQLException.class); assertThat(((SQLException)error).getSQLState()).isEqualTo(expected);
    }
}
