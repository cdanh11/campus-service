package com.campus.reporting.infrastructure;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.reporting.application.*;
import com.campus.shared.application.reporting.*;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Synthetic application/database measurements, not an HTTP throughput or production SLA. */
@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class ReportingQueryLoadIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DetailReportService reports;
    @Autowired DashboardService dashboard;
    @MockitoSpyBean NamedParameterJdbcTemplate named;
    record Query(String sql,Map<String,Object> parameters) { }

    @Test void actualOwnerQueriesUseExistingIndexesAndBoundedConcurrentReadWorkloadCompletes() throws Exception {
        seed();
        UUID student=jdbc.queryForObject("SELECT id FROM students WHERE student_number='S1'",UUID.class);
        var queries=new ArrayList<Query>();
        doAnswer(invocation->{
            queries.add(new Query(invocation.getArgument(0),new HashMap<>((Map<String,Object>)invocation.getArgument(1))));
            return invocation.callRealMethod();
        }).when(named).query(anyString(),anyMap(),any(RowMapper.class));
        try {
            for(var kind:ReportKind.values()) {
                var result=reports.search(kind,search(student));
                assertThat(result.totalElements()).isEqualTo(1);assertThat(result.content()).hasSize(1);
            }
        } finally { reset(named); }
        assertThat(queries).hasSize(5);
        for(int i=0;i<queries.size();i++) {
            var query=queries.get(i);
            var plan=json.readTree(named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+query.sql(),query.parameters(),String.class));
            var indexes=plan.findValuesAsText("Index Name");
            String expected=switch(ReportKind.values()[i]) {
                case STUDENT_DEBT -> "ix_finance_charge_student_status";
                case CURRENT_ACCOMMODATION -> "ux_dormitory_assignment_current_student";
                case SECTION_ENROLLMENT -> "ix_academic_enrollment_student_status";
                case EVENT_MEMBERSHIP -> "ix_event_registration_student_status";
                case LIBRARY_LOANS -> "ix_library_loan_student_status";
            };
            if(ReportKind.values()[i]==ReportKind.CURRENT_ACCOMMODATION)
                assertThat(indexes).containsAnyOf(expected,"ix_dormitory_assignment_student_status");
            else if(ReportKind.values()[i]==ReportKind.SECTION_ENROLLMENT)
                assertThat(indexes).containsAnyOf(expected,"ux_academic_enrollment_student_section");
            else assertThat(indexes).as(ReportKind.values()[i].name()).contains(expected);
            System.out.println("REPORT_PLAN "+ReportKind.values()[i]+" executionMs="+plan.get(0).path("Execution Time")+" indexes="+indexes);
        }
        var summary=dashboard.dashboard();
        assertThat(summary.groups().get("FINANCE").get("outstanding_vnd")).isEqualByComparingTo("7000000");
        assertThat(summary.groups().get("DORMITORY").get("occupied_beds")).isEqualByComparingTo("10000");
        assertThat(summary.groups().get("LIBRARY").get("open_loans")).isEqualByComparingTo("10000");
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        var durations=Collections.synchronizedList(new ArrayList<Long>());var tasks=new ArrayList<Future<?>>();
        long wall=System.nanoTime();
        try {
            for(int worker=0;worker<4;worker++) tasks.add(pool.submit(()->{
                if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Workload start timeout");
                for(int iteration=0;iteration<5;iteration++) {
                    for(var kind:ReportKind.values()) {
                        long before=System.nanoTime();
                        var result=reports.search(kind,search(iteration%2==0?student:null));
                        assertThat(result.totalElements()).isEqualTo(iteration%2==0?1:10000);
                        assertThat(result.content()).hasSize(iteration%2==0?1:20);
                        durations.add(System.nanoTime()-before);
                    }
                    long before=System.nanoTime(); assertThat(dashboard.dashboard().groups()).hasSize(8);durations.add(System.nanoTime()-before);
                }
                return null;
            }));
            start.countDown(); for(var task:tasks) task.get(60,TimeUnit.SECONDS);
        } finally { start.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
        var sorted=durations.stream().sorted().toList();assertThat(sorted).hasSize(120);
        System.out.printf(Locale.ROOT,"REPORT_LOAD rowsPerOwner=10000 receipts=20000 workers=4 requests=120 wallMs=%.3f p50Ms=%.3f p95Ms=%.3f maxMs=%.3f%n",
                (System.nanoTime()-wall)/1_000_000.0,sorted.get(59)/1_000_000.0,sorted.get(113)/1_000_000.0,sorted.getLast()/1_000_000.0);
    }
    private ReportSearch search(UUID student) { return new ReportSearch(0,20,student,null,null,null,null,false,true); }
    private void seed() {
        UUID unit=UUID.randomUUID(),course=UUID.randomUUID(),term=UUID.randomUUID(),offering=UUID.randomUUID(),section=UUID.randomUUID();
        UUID building=UUID.randomUUID(),room=UUID.randomUUID(),title=UUID.randomUUID(),event=UUID.randomUUID(),fee=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,'UNIT','Unit','FACULTY')",unit);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) SELECT gen_random_uuid(),'S'||n,'Student '||n,? FROM generate_series(1,10000) n",unit);
        jdbc.update("INSERT INTO academic_courses(id,code,title,credits,organization_unit_id) VALUES (?,'COURSE','Course',3,?)",course,unit);
        jdbc.update("INSERT INTO academic_terms(id,code,name,start_date,end_date) VALUES (?,'TERM','Term',CURRENT_DATE,CURRENT_DATE)",term);
        jdbc.update("INSERT INTO academic_course_offerings(id,term_id,course_id,organization_unit_id) VALUES (?,?,?,?)",offering,term,course,unit);
        jdbc.update("INSERT INTO academic_class_sections(id,offering_id,code,capacity) VALUES (?,?,'SECTION',10000)",section,offering);
        jdbc.update("INSERT INTO academic_enrollments(id,student_id,section_id) SELECT gen_random_uuid(),id,? FROM students",section);
        jdbc.update("INSERT INTO dormitory_buildings(id,code,name) VALUES (?,'BUILDING','Building')",building);
        jdbc.update("INSERT INTO dormitory_rooms(id,building_id,code,name) VALUES (?,?,'ROOM','Room')",room,building);
        jdbc.update("INSERT INTO dormitory_beds(id,room_id,code,name) SELECT gen_random_uuid(),?,student_number,'Bed' FROM students",room);
        jdbc.execute("INSERT INTO dormitory_assignments(id,student_id,bed_id) SELECT gen_random_uuid(),s.id,b.id FROM students s JOIN dormitory_beds b ON b.code=s.student_number");
        jdbc.update("INSERT INTO library_titles(id,code,title,author) VALUES (?,'TITLE','Title','Author')",title);
        jdbc.update("INSERT INTO library_copies(id,title_id,code) SELECT gen_random_uuid(),?,student_number FROM students",title);
        jdbc.execute("INSERT INTO library_loans(id,student_id,copy_id) SELECT gen_random_uuid(),s.id,c.id FROM students s JOIN library_copies c ON c.code=s.student_number");
        jdbc.update("INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity) VALUES (?,'EVENT','Event','Description',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day',10000)",event);
        jdbc.update("INSERT INTO event_registrations(id,student_id,event_id) SELECT gen_random_uuid(),id,? FROM students",event);
        jdbc.update("INSERT INTO finance_fee_definitions(id,code,name,amount) VALUES (?,'FEE','Fee',1000)",fee);
        jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date) SELECT gen_random_uuid(),student_number,id,?,'FEE','Fee',1000,CURRENT_DATE FROM students",fee);
        jdbc.execute("INSERT INTO finance_manual_payments(id,receipt_number,charge_id,amount) SELECT gen_random_uuid(),charge_number||'-'||n,id,n*100 FROM finance_student_charges CROSS JOIN generate_series(1,2) n");
        for(String table:List.of("students","academic_enrollments","academic_class_sections","academic_course_offerings","dormitory_assignments","dormitory_beds","dormitory_rooms","library_loans","library_copies","library_titles","event_registrations","campus_events","finance_student_charges","finance_manual_payments")) jdbc.execute("ANALYZE "+table);
    }
}
