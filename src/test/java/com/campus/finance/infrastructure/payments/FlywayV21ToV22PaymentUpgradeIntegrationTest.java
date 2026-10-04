package com.campus.finance.infrastructure.payments;

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
class FlywayV21ToV22PaymentUpgradeIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
    static JdbcTemplate jdbc;
    static UUID actor=UUID.randomUUID(),student=UUID.randomUUID(),legacyFee=UUID.randomUUID(),legacyCharge=UUID.randomUUID();
    static Map<String,List<Map<String,Object>>> legacy;
    static List<Map<String,Object>> history;
    @BeforeAll static void upgrade() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        assertThat(flyway("21").migrate().migrationsExecuted).isEqualTo(21);
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
        legacy=snapshot(); history=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(flyway("22").migrate().migrationsExecuted).isEqualTo(1);
    }
    @Test void preservesAllPriorTablesAndAppliesOnlyV22() {
        assertThat(snapshot()).isEqualTo(legacy);
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE installed_rank<=21 ORDER BY installed_rank")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE installed_rank>21",String.class)).containsExactly("22");
        assertThat(flyway("22").validateWithResult().validationSuccessful).isTrue();
    }
    @Test void verifiesPaymentColumnsConstraintsDefaultsIndexesAndAuditCompatibility() {
        var columns=new LinkedHashMap<String,Map<String,Object>>();
        for(var column:jdbc.queryForList("SELECT column_name,data_type,character_maximum_length,is_nullable,column_default,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema='public' AND table_name='finance_manual_payments'")) columns.put(column.get("column_name").toString(),column);
        var types=new LinkedHashMap<String,String>();
        types.put("id","uuid");types.put("receipt_number","character varying");types.put("charge_id","uuid");types.put("amount","numeric");
        types.put("currency","character varying");types.put("status","character varying");types.put("row_version","bigint");
        types.put("recorded_at","timestamp with time zone");types.put("reversed_at","timestamp with time zone");types.put("reversal_reason","character varying");
        types.put("created_at","timestamp with time zone");types.put("updated_at","timestamp with time zone");
        var lengths=Map.of("receipt_number",32,"currency",3,"status",16,"reversal_reason",500);
        var defaults=Map.of("currency","VND","status","RECORDED","row_version","0","recorded_at","CURRENT_TIMESTAMP","created_at","CURRENT_TIMESTAMP","updated_at","CURRENT_TIMESTAMP");
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(types.keySet());
        types.forEach((column,type) -> {
            assertThat(columns.get(column)).containsEntry("data_type",type).containsEntry("is_nullable",Set.of("reversed_at","reversal_reason").contains(column)?"YES":"NO");
            assertThat(columns.get(column).get("character_maximum_length")).isEqualTo(lengths.get(column));
            if(defaults.containsKey(column)) assertThat(columns.get(column).get("column_default").toString()).contains(defaults.get(column));
            else assertThat(columns.get(column).get("column_default")).isNull();
        });
        assertThat(columns.get("amount")).containsEntry("numeric_precision",null).containsEntry("numeric_scale",null);
        assertThat(jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='finance_manual_payments'::regclass AND contype IN ('p','f')",String.class))
                .containsExactlyInAnyOrder("PRIMARY KEY (id)","FOREIGN KEY (charge_id) REFERENCES finance_student_charges(id)");
        var indexes=jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='finance_manual_payments'",String.class);
        assertThat(indexes).anySatisfy(index -> assertThat(index).contains("UNIQUE","lower","receipt_number")).anyMatch(index -> index.contains("(charge_id, status)"));
        UUID id=UUID.randomUUID(); receipt(id,"RECEIPT");
        var row=jdbc.queryForMap("SELECT * FROM finance_manual_payments WHERE id=?",id);
        assertThat(row).containsEntry("currency","VND").containsEntry("status","RECORDED").containsEntry("row_version",0L).containsEntry("reversed_at",null).containsEntry("reversal_reason",null);
        for(String column:List.of("recorded_at","created_at","updated_at")) assertThat(row.get(column)).isNotNull();
        for(String column:types.keySet()) if(!Set.of("reversed_at","reversal_reason").contains(column))
            state("23502",() -> jdbc.update("UPDATE finance_manual_payments SET "+column+"=NULL WHERE id=?",id));
        state("23505",() -> jdbc.update("INSERT INTO finance_manual_payments SELECT * FROM finance_manual_payments WHERE id=?",id));
        state("23503",() -> jdbc.update("UPDATE finance_manual_payments SET charge_id=? WHERE id=?",UUID.randomUUID(),id));
        for(String amount:List.of("0","-1","0.001","1.00001","10000000000000000000","NaN","Infinity"))
            state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET amount=?::numeric WHERE id=?",amount,id));
        for(String amount:List.of("1","9999999999999999999")) {
            jdbc.update("UPDATE finance_manual_payments SET amount=?::numeric WHERE id=?",amount,id);
            assertThat(jdbc.queryForObject("SELECT amount FROM finance_manual_payments WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo(amount);
        }
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET currency='USD' WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET row_version=-1 WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='UNKNOWN' WHERE id=?",id));
        state("22001",() -> jdbc.update("UPDATE finance_manual_payments SET status=? WHERE id=?","X".repeat(17),id));
        for(String text:List.of("x"," ","\t","\n","\r","\u000b","\f"," \t\n\r\u000b\f "))
            state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET receipt_number=? WHERE id=?",text,id));
        state("22001",() -> jdbc.update("UPDATE finance_manual_payments SET receipt_number=? WHERE id=?","X".repeat(33),id));
        jdbc.update("UPDATE finance_manual_payments SET receipt_number=? WHERE id=?","😀".repeat(32),id);
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET reversed_at=recorded_at WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET reversal_reason='Reason' WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED' WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at,reversal_reason=NULL WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=NULL,reversal_reason='Reason' WHERE id=?",id));
        state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at-INTERVAL '1 second',reversal_reason='Reason' WHERE id=?",id));
        for(String reason:List.of("x"," ","\t","\n","\r","\u000b","\f"," \t\n\r\u000b\f "))
            state("23514",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at,reversal_reason=? WHERE id=?",reason,id));
        state("22001",() -> jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at,reversal_reason=? WHERE id=?","😀".repeat(501),id));
        jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at,reversal_reason=?,receipt_number='REVERSED-NUMBER' WHERE id=?","😀".repeat(500),id);
        state("23505",() -> receipt(UUID.randomUUID(),"reversed-number"));
        UUID event=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO finance_audit_events (id,actor_user_id,resource_type,target_id,action,resource_version) VALUES (?,?,'PAYMENT',?,'RECORDED',0)",event,actor,id);
            jdbc.update("UPDATE finance_audit_events SET action='REVERSED',resource_version=1 WHERE id=?",event);
            state("23514",() -> jdbc.update("UPDATE finance_audit_events SET action='CREATED' WHERE id=?",event));
            state("23514",() -> jdbc.update("UPDATE finance_audit_events SET resource_type='CHARGE' WHERE id=?",event));
            jdbc.update("UPDATE finance_audit_events SET resource_type='FEE',action='UPDATED' WHERE id=?",event);
            state("23514",() -> jdbc.update("UPDATE finance_audit_events SET action='CANCELLED' WHERE id=?",event));
            jdbc.update("UPDATE finance_audit_events SET resource_type='CHARGE',action='CANCELLED' WHERE id=?",event);
            state("23514",() -> jdbc.update("UPDATE finance_audit_events SET action='UPDATED' WHERE id=?",event));
        } finally { jdbc.update("DELETE FROM finance_audit_events WHERE id=?",event); }
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Test void validatesAll25ProductionEntitiesOnExactUpgradeWithoutFlywayOrSchemaCreation() {
        var before=jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class))
                .withUserConfiguration(ValidationConfiguration.class)
                .withPropertyValues("spring.datasource.url="+postgres.getJdbcUrl(),"spring.datasource.username="+postgres.getUsername(),
                        "spring.datasource.password="+postgres.getPassword(),"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.properties.hibernate.default_schema=public")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(EntityManagerFactory.class).doesNotHaveBean(Flyway.class);
                    var model=context.getBean(EntityManagerFactory.class).getMetamodel();assertThat(model.getEntities()).hasSize(25);
                    assertThat(model.entity(ManualPaymentEntity.class)).isNotNull();
                });
        assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        assertThat(snapshot()).isEqualTo(legacy);
    }
    @Configuration(proxyBeanMethods=false) @EntityScan({
            "com.campus.identity.infrastructure.persistence.entity", "com.campus.organization.infrastructure.persistence",
            "com.campus.student.infrastructure.persistence", "com.campus.personnel.infrastructure.persistence",
            "com.campus.shared.infrastructure.persistence", "com.campus.academic.infrastructure.persistence",
            "com.campus.dormitory.infrastructure.persistence", "com.campus.dormitory.infrastructure.assignment",
            "com.campus.finance.infrastructure.obligations", "com.campus.finance.infrastructure.payments"
    }) static class ValidationConfiguration { }
    private void receipt(UUID id,String number) { jdbc.update("INSERT INTO finance_manual_payments (id,receipt_number,charge_id,amount) VALUES (?,?,?,1)",id,number,legacyCharge); }
    private static Flyway flyway(String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(target).load(); }
    private static Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('flyway_schema_history','finance_manual_payments') ORDER BY tablename",String.class))
            result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1"));return result;
    }
    private void state(String expected,Runnable task) {
        Throwable failure=catchThrowable(task::run);assertThat(failure).isNotNull();
        while(failure.getCause()!=null) failure=failure.getCause();
        assertThat(failure).isInstanceOf(SQLException.class);assertThat(((SQLException)failure).getSQLState()).isEqualTo(expected);
    }
}
