package com.campus.finance.infrastructure.obligations;

import java.math.BigDecimal;
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
class FlywayV20ToV21FinanceUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor=UUID.randomUUID(), student=UUID.randomUUID();
    static Map<String,List<Map<String,Object>>> legacy;
    static List<Map<String,Object>> history;
    @BeforeAll static void upgrade() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        assertThat(flyway("20").migrate().migrationsExecuted).isEqualTo(20);
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
        legacy=snapshot(); history=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("21").migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void preservesEveryLegacyTableAndAppliesOnlyV21() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=20 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>20",String.class)).containsExactly("21");
        assertThat(flyway("21").validateWithResult().validationSuccessful).isTrue();
    }
    @Test void verifiesFeeAndChargeSchemaBoundariesDefaultsConstraintsAndIndexes() {
        var feeTypes=new LinkedHashMap<String,String>();
        feeTypes.put("id","uuid"); feeTypes.put("code","character varying"); feeTypes.put("name","character varying");
        feeTypes.put("amount","numeric"); feeTypes.put("currency","character varying"); feeTypes.put("status","character varying");
        feeTypes.put("row_version","bigint"); feeTypes.put("created_at","timestamp with time zone"); feeTypes.put("updated_at","timestamp with time zone");
        columns("finance_fee_definitions",feeTypes,Map.of("code",32,"name",160,"currency",3,"status",16),
                Map.of("currency","VND","status","ACTIVE","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        var chargeTypes=new LinkedHashMap<>(feeTypes); chargeTypes.remove("code");chargeTypes.remove("name");
        chargeTypes.put("charge_number","character varying");chargeTypes.put("student_id","uuid");chargeTypes.put("fee_id","uuid");
        chargeTypes.put("fee_code","character varying");chargeTypes.put("fee_name","character varying");chargeTypes.put("due_date","date");
        columns("finance_student_charges",chargeTypes,Map.of("charge_number",32,"fee_code",32,"fee_name",160,"currency",3,"status",16),
                Map.of("currency","VND","status","OPEN","row_version","0","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP"));
        assertThat(keys("finance_fee_definitions")).containsExactly("PRIMARY KEY (id)");
        assertThat(keys("finance_student_charges")).containsExactlyInAnyOrder("PRIMARY KEY (id)","FOREIGN KEY (student_id) REFERENCES students(id)","FOREIGN KEY (fee_id) REFERENCES finance_fee_definitions(id)");
        assertThat(indexes("finance_fee_definitions")).anySatisfy(index -> assertThat(index).contains("UNIQUE","lower", "code")).anyMatch(index -> index.contains("(status)"));
        assertThat(indexes("finance_student_charges")).anySatisfy(index -> assertThat(index).contains("UNIQUE","lower","charge_number"))
                .anyMatch(index -> index.contains("(student_id, status)")).anyMatch(index -> index.contains("(fee_id, status)")).anyMatch(index -> index.contains("(due_date)"));
        UUID fee=fee(),charge=charge(fee);
        var feeRow=jdbc.queryForMap("SELECT * FROM finance_fee_definitions WHERE id=?",fee);
        var chargeRow=jdbc.queryForMap("SELECT * FROM finance_student_charges WHERE id=?",charge);
        assertThat(feeRow).containsEntry("currency","VND").containsEntry("status","ACTIVE").containsEntry("row_version",0L);
        assertThat(chargeRow).containsEntry("currency","VND").containsEntry("status","OPEN").containsEntry("row_version",0L);
        for(String table:List.of("finance_fee_definitions","finance_student_charges")) {
            UUID id=table.endsWith("definitions")?fee:charge;
            var types=table.endsWith("definitions")?feeTypes:chargeTypes;
            for(String column:types.keySet()) state("23502",() -> jdbc.update("UPDATE "+table+" SET "+column+"=NULL WHERE id=?",id));
            state("23505",() -> jdbc.update("INSERT INTO "+table+" SELECT * FROM "+table+" WHERE id=?",id));
            for(String invalid:List.of("0","-1","1.01","1.001","0.00001","10000000000000000000","NaN","Infinity"))
                state("23514",() -> jdbc.update("UPDATE "+table+" SET amount=?::numeric WHERE id=?",invalid,id));
            for(String valid:List.of("1","9999999999999999999")) {
                jdbc.update("UPDATE "+table+" SET amount=?::numeric WHERE id=?",valid,id);
                assertThat(jdbc.queryForObject("SELECT amount FROM "+table+" WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo(valid);
            }
            state("23514",() -> jdbc.update("UPDATE "+table+" SET currency='USD' WHERE id=?",id));
            state("23514",() -> jdbc.update("UPDATE "+table+" SET status='UNKNOWN' WHERE id=?",id));
            state("22001",() -> jdbc.update("UPDATE "+table+" SET status=? WHERE id=?","X".repeat(17),id));
            state("23514",() -> jdbc.update("UPDATE "+table+" SET row_version=-1 WHERE id=?",id));
        }
        for(String column:List.of("student_id","fee_id")) state("23503",() -> jdbc.update("UPDATE finance_student_charges SET "+column+"=? WHERE id=?",UUID.randomUUID(),charge));
        for(var spec:List.of(Map.entry("finance_fee_definitions","code"),Map.entry("finance_student_charges","charge_number"),Map.entry("finance_student_charges","fee_code"))) {
            UUID id=spec.getKey().endsWith("definitions")?fee:charge;
            for(String value:List.of("x"," ","\t","\n","\r","\u000b","\f"," \t\n\r\u000b\f ")) state("23514",() -> jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?",value,id));
            state("22001",() -> jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?","X".repeat(33),id));
            jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?","😀".repeat(32),id);
        }
        for(var spec:List.of(Map.entry("finance_fee_definitions","name"),Map.entry("finance_student_charges","fee_name"))) {
            UUID id=spec.getKey().endsWith("definitions")?fee:charge;
            state("23514",() -> jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?"," \t\n\r\u000b\f ",id));
            state("23514",() -> jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"='x' WHERE id=?",id));
            state("22001",() -> jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?","😀".repeat(161),id));
            jdbc.update("UPDATE "+spec.getKey()+" SET "+spec.getValue()+"=? WHERE id=?","😀".repeat(160),id);
        }
        UUID otherFee=fee(),otherCharge=charge(otherFee);
        jdbc.update("UPDATE finance_fee_definitions SET code='DUPLICATE' WHERE id=?",fee);
        state("23505",() -> jdbc.update("UPDATE finance_fee_definitions SET code='duplicate' WHERE id=?",otherFee));
        jdbc.update("UPDATE finance_student_charges SET charge_number='DUPLICATE' WHERE id=?",charge);
        state("23505",() -> jdbc.update("UPDATE finance_student_charges SET charge_number='duplicate' WHERE id=?",otherCharge));
        jdbc.update("UPDATE finance_fee_definitions SET status='INACTIVE' WHERE id=?",fee);
        jdbc.update("UPDATE finance_student_charges SET status='CANCELLED' WHERE id=?",charge);
    }
    @Test void verifiesCompleteAuditSchemaAndApprovedPolicyWithoutMetadataLengthCap() {
        columns("finance_audit_events",Map.of("id","uuid","actor_user_id","uuid","resource_type","character varying","target_id","uuid",
                "action","character varying","resource_version","bigint","occurred_at","timestamp with time zone","metadata","jsonb"),
                Map.of("resource_type",16,"action",16),Map.of("occurred_at","CURRENT_TIMESTAMP","metadata","{}"));
        assertThat(keys("finance_audit_events")).containsExactlyInAnyOrder("PRIMARY KEY (id)","FOREIGN KEY (actor_user_id) REFERENCES identity_users(id)");
        assertThat(indexes("finance_audit_events")).anyMatch(index -> index.contains("(resource_type, target_id, occurred_at)"));
        UUID event=UUID.randomUUID();
        jdbc.update("INSERT INTO finance_audit_events (id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'FEE',?,'CREATED',0)",event,actor,UUID.randomUUID());
        assertThat(jdbc.queryForMap("SELECT * FROM finance_audit_events WHERE id=?",event)).containsEntry("resource_version",0L).hasEntrySatisfying("occurred_at",value -> assertThat(value).isNotNull());
        assertThat(jdbc.queryForObject("SELECT metadata::text FROM finance_audit_events WHERE id=?",String.class,event)).isEqualTo("{}");
        for(String column:List.of("id","actor_user_id","resource_type","target_id","action","resource_version","occurred_at","metadata"))
            state("23502",() -> jdbc.update("UPDATE finance_audit_events SET "+column+"=NULL WHERE id=?",event));
        state("23505",() -> jdbc.update("INSERT INTO finance_audit_events SELECT * FROM finance_audit_events WHERE id=?",event));
        state("23503",() -> jdbc.update("UPDATE finance_audit_events SET actor_user_id=? WHERE id=?",UUID.randomUUID(),event));
        state("23514",() -> jdbc.update("UPDATE finance_audit_events SET resource_version=-1 WHERE id=?",event));
        for(String column:List.of("action","resource_type")) state("22001",() -> jdbc.update("UPDATE finance_audit_events SET "+column+"=? WHERE id=?","X".repeat(17),event));
        for(String json:List.of("[]","null","1","\"text\"")) state("23514",() -> jdbc.update("UPDATE finance_audit_events SET metadata=?::jsonb WHERE id=?",json,event));
        jdbc.update("UPDATE finance_audit_events SET metadata=?::jsonb WHERE id=?","{\"unicode\":\""+ "😀".repeat(10000)+"\"}",event);
        jdbc.update("UPDATE finance_audit_events SET action='UPDATED' WHERE id=?",event);
        state("23514",() -> jdbc.update("UPDATE finance_audit_events SET action='CANCELLED' WHERE id=?",event));
        jdbc.update("UPDATE finance_audit_events SET resource_type='CHARGE',action='CANCELLED' WHERE id=?",event);
        jdbc.update("UPDATE finance_audit_events SET action='CREATED' WHERE id=?",event);
        state("23514",() -> jdbc.update("UPDATE finance_audit_events SET action='UPDATED' WHERE id=?",event));
        state("23514",() -> jdbc.update("UPDATE finance_audit_events SET resource_type='UNKNOWN' WHERE id=?",event));
    }
    @Test void validatesAll24ProductionEntitiesOnExactV21SchemaWithFlywayDisabled() {
        var before=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username="+postgres.getUsername(),
                        "spring.datasource.password="+postgres.getPassword(),"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var model=context.getBean(EntityManagerFactory.class).getMetamodel(); assertThat(model.getEntities()).hasSize(24);
                    assertThat(model.entity(StudentChargeEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Configuration(proxyBeanMethods=false) @EntityScan("com.campus") static class ValidationConfiguration { }
    private UUID fee() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO finance_fee_definitions (id,code,name,amount) VALUES (?,?,'Fee',100000)",id,id.toString().substring(0,8));return id;
    }
    private UUID charge(UUID fee) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO finance_student_charges (id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date) VALUES (?,?,?,?,'FE','Fee',100000,DATE '2026-01-01')",id,id.toString().substring(0,8),student,fee);return id;
    }
    private void columns(String table,Map<String,String> types,Map<String,Integer> lengths,Map<String,String> defaults) {
        var columns=new LinkedHashMap<String,Map<String,Object>>();
        for(var column:jdbc.queryForList("SELECT column_name,data_type,character_maximum_length,is_nullable,column_default,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema='public' AND table_name=?",table)) columns.put(column.get("column_name").toString(),column);
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(types.keySet());
        types.forEach((column,type) -> {
            assertThat(columns.get(column)).containsEntry("data_type",type).containsEntry("is_nullable","NO");
            assertThat(columns.get(column).get("character_maximum_length")).isEqualTo(lengths.get(column));
            if(defaults.containsKey(column)) assertThat(columns.get(column).get("column_default").toString()).contains(defaults.get(column));
            else assertThat(columns.get(column).get("column_default")).isNull();
            if(type.equals("numeric")) assertThat(columns.get(column)).containsEntry("numeric_precision",null).containsEntry("numeric_scale",null);
        });
    }
    private List<String> keys(String table) { return jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid=?::regclass AND contype IN ('p','f')",String.class,table); }
    private List<String> indexes(String table) { return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND tablename=?",String.class,table); }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','finance_fee_definitions','finance_student_charges','finance_audit_events') ORDER BY tablename",String.class))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1"));return result;
    }
    private void state(String expected,Runnable task) {
        Throwable failure=catchThrowable(task::run);assertThat(failure).isNotNull();
        while(failure.getCause()!=null) failure=failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class);assertThat(((SQLException)failure).getSQLState()).isEqualTo(expected);
    }
}
