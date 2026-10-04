package com.campus.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.*;
import com.campus.reporting.application.DetailReportService;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest @Transactional
class DetailReportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DetailReportService reports;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    static final String ROOT="/api/v1/admin/reports/";
    final Instant time=Instant.parse("2026-01-01T00:00:00Z");
    UUID student,other,unit,section,event,copy,title,charge;
    String admin,user;

    @BeforeEach void seed() {
        admin=token(RoleCode.ADMIN); user=token(RoleCode.USER);
        unit=UUID.randomUUID(); student=UUID.randomUUID();other=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,'UNIT','Unit','FACULTY')",unit);
        for(UUID id:List.of(student,other)) jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) VALUES (?,?,'Student',?)",id,id.toString().substring(0,20),unit);
        finance(); dormitory(); academic(); event(); library();
    }
    @Test void everyReportAndExportRequiresAdminAndAdvertisesBearer() throws Exception {
        var spec=read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String path:List.of(ROOT+"{report}",ROOT+"{report}/export"))
            assertThat(spec.path("paths").path(path).path("get").path("security").get(0).has("bearerAuth")).isTrue();
        for(var kind:ReportKind.values()) for(String suffix:List.of("","/export")) {
            call(get(ROOT+kind+suffix),"").andExpect(status().isUnauthorized());
            call(get(ROOT+kind+suffix),user).andExpect(status().isForbidden());
            call(get(ROOT+kind+suffix),admin).andExpect(status().isOk());
        }
    }
    @Test void eachProjectionHasFixedFieldsAndSupportsStudentResourceAndStateFilters() throws Exception {
        for(var kind:ReportKind.values()) {
            var result=read(call(get(ROOT+kind).param("studentId",student.toString()),admin).andExpect(status().isOk()));
            assertThat(result.path("totalElements").asLong()).isEqualTo(1);
            var fields=new ArrayList<String>(); result.path("content").get(0).fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyElementsOf(kind.columns());
            assertThat(result.toString()).doesNotContain("email","password","metadata","fullName");
            call(get(ROOT+kind).param("studentId",UUID.randomUUID().toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        call(get(ROOT+ReportKind.SECTION_ENROLLMENT).param("resourceId",section.toString()).param("status","ENROLLED"),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        call(get(ROOT+ReportKind.EVENT_MEMBERSHIP).param("resourceId",event.toString()).param("status","ATTENDED"),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        call(get(ROOT+ReportKind.LIBRARY_LOANS).param("resourceId",copy.toString()).param("overdueOnly","true"),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void paginationSortAndTimeWindowAreStableAndConsistent() throws Exception {
        for(var kind:ReportKind.values()) {
            var ascending=read(call(get(ROOT+kind).param("size","1"),admin).andExpect(status().isOk()));
            var next=read(call(get(ROOT+kind).param("size","1").param("page","1"),admin).andExpect(status().isOk()));
            var descending=read(call(get(ROOT+kind).param("size","1").param("sort","id,desc"),admin).andExpect(status().isOk()));
            assertThat(ascending.path("totalElements").asLong()).isEqualTo(2);
            assertThat(ascending.path("totalPages").asLong()).isEqualTo(2);
            assertThat(next.path("content")).isEqualTo(descending.path("content"));
            assertThat(next.path("content")).isNotEqualTo(ascending.path("content"));
            call(get(ROOT+kind).param("from",time.toString()).param("until",time.plusSeconds(1).toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
            call(get(ROOT+kind).param("until",time.toString()),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
    }
    @Test void invalidInputsReturnUniform400AndUnsupportedFiltersAreNotSilentlyIgnored() throws Exception {
        for(var kind:ReportKind.values()) {
            for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","101"},new String[]{"size","0"},new String[]{"page","2147483647"},new String[]{"studentId","bad"},new String[]{"sort","id;DROP"},new String[]{"status","UNKNOWN"},new String[]{"from","yesterday"},new String[]{"overdueOnly","invalid"}))
                call(get(ROOT+kind).param(bad[0],bad[1]),admin).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
            call(get(ROOT+kind).param("from",time.toString()).param("until",time.toString()),admin).andExpect(status().isBadRequest());
        }
        call(get(ROOT+"UNKNOWN"),admin).andExpect(status().isBadRequest());
        call(get(ROOT+ReportKind.STUDENT_DEBT).param("resourceId",copy.toString()),admin).andExpect(status().isBadRequest());
        call(get(ROOT+ReportKind.EVENT_MEMBERSHIP).param("overdueOnly","true"),admin).andExpect(status().isBadRequest());
    }
    @Test void correlationHeaderMatchesReportErrorAndAppearsOnSecurityFailures() throws Exception {
        String id=UUID.randomUUID().toString();
        call(get(ROOT+ReportKind.LIBRARY_LOANS).param("size","0").header("X-Request-ID",id),admin)
                .andExpect(status().isBadRequest()).andExpect(header().string("X-Request-ID",id)).andExpect(jsonPath("$.traceId").value(id));
        call(get(ROOT+ReportKind.LIBRARY_LOANS).header("X-Request-ID",id),"")
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID",id));
        call(get(ROOT+ReportKind.LIBRARY_LOANS).header("X-Request-ID",id),user)
                .andExpect(status().isForbidden()).andExpect(header().string("X-Request-ID",id));
        var response=call(get(ROOT+ReportKind.LIBRARY_LOANS).header("X-Request-ID","invalid"),admin)
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(UUID.fromString(response.getHeader("X-Request-ID")).toString()).isEqualTo(response.getHeader("X-Request-ID"));
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        call(get("/actuator/metrics"),admin).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.details").doesNotExist());
    }
    @Test void debtPreaggregatesReceiptsKeepsExactLargeVndAndExcludesCancelledCharges() {
        var row=(ReportRow.Debt)reports.search(ReportKind.STUDENT_DEBT,query(student)).content().getFirst();
        assertThat(row.principalVnd()).isEqualByComparingTo("19999999999999999998");
        assertThat(row.paidVnd()).isEqualByComparingTo("300");
        assertThat(row.outstandingVnd()).isEqualByComparingTo("19999999999999999698");
        jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=recorded_at,reversal_reason='Correction' WHERE charge_id=? AND status='RECORDED'",charge);
        row=(ReportRow.Debt)reports.search(ReportKind.STUDENT_DEBT,query(student)).content().getFirst();
        assertThat(row.paidVnd()).isEqualByComparingTo(BigDecimal.ZERO);
        jdbc.update("UPDATE finance_student_charges SET status='CANCELLED' WHERE student_id=?",student);
        assertThat(reports.search(ReportKind.STUDENT_DEBT,query(student)).content()).isEmpty();
    }
    @Test void csvPreservesUnicodeEscapesQuotesAndNeutralizesFormulas() throws Exception {
        var response=call(get(ROOT+ReportKind.LIBRARY_LOANS+"/export").param("studentId",student.toString()),admin)
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\"id\",\"studentId\"").contains("\"'=SUM(1,2)\r\n\"\"Sách\"\"\"");
        assertThat(csv).contains(student.toString()).doesNotContain(other.toString());
    }
    @Test void csvAllowsExactly5000RowsRejects5001AndStillAllowsFilteredExport() throws Exception {
        jdbc.update("INSERT INTO library_copies(id,title_id,code) SELECT gen_random_uuid(),?, 'BULK-'||n FROM generate_series(1,4998) n",title);
        jdbc.update("INSERT INTO library_loans(id,copy_id,student_id,borrowed_at,due_at) SELECT gen_random_uuid(),id,?,?,? FROM library_copies WHERE code LIKE 'BULK-%'",student,stamp(time),stamp(time.plusSeconds(100)));
        var csv=call(get(ROOT+ReportKind.LIBRARY_LOANS+"/export"),admin).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Title contains a newline, so physical line counts are not CSV row counts.
        assertThat(csv.split("\"false\"\\r\\n|\"true\"\\r\\n",-1)).hasSize(5001);
        UUID added=UUID.randomUUID();
        jdbc.update("INSERT INTO library_copies(id,title_id,code) VALUES (?,?,'EXTRA')",added,title);
        jdbc.update("INSERT INTO library_loans(id,copy_id,student_id,borrowed_at,due_at) VALUES (?,?,?,?,?)",UUID.randomUUID(),added,student,stamp(time),stamp(time.plusSeconds(100)));
        call(get(ROOT+ReportKind.LIBRARY_LOANS+"/export"),admin).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REPORT_EXPORT_LIMIT_EXCEEDED"));
        call(get(ROOT+ReportKind.LIBRARY_LOANS+"/export").param("resourceId",added.toString()),admin).andExpect(status().isOk());
        call(get(ROOT+ReportKind.LIBRARY_LOANS).param("size","100"),admin).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5001)).andExpect(jsonPath("$.content.length()").value(100));
    }
    private ReportSearch query(UUID student) { return new ReportSearch(0,100,student,null,null,null,null,false,true); }
    private void finance() {
        UUID fee=UUID.randomUUID(); charge=UUID.randomUUID();
        jdbc.update("INSERT INTO finance_fee_definitions(id,code,name,amount) VALUES (?,'FEE','Fee',1)",fee);
        for(UUID id:List.of(charge,UUID.randomUUID())) jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date,created_at) VALUES (?,?,?,?,'FEE','Fee',9999999999999999999,CURRENT_DATE,?)",id,id.toString().substring(0,20),student,fee,stamp(time));
        jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date,created_at) VALUES (?,'OTHER',?,?,'FEE','Fee',1000,CURRENT_DATE,?)",UUID.randomUUID(),other,fee,stamp(time));
        jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date,status,created_at) VALUES (?,'CANCEL',?,?,'FEE','Fee',900,CURRENT_DATE,'CANCELLED',?)",UUID.randomUUID(),student,fee,stamp(time));
        for(int i=1;i<=3;i++) jdbc.update("INSERT INTO finance_manual_payments(id,receipt_number,charge_id,amount,status,reversed_at,reversal_reason) VALUES (?,?,?,?,?,CASE WHEN ?='REVERSED' THEN CURRENT_TIMESTAMP END,CASE WHEN ?='REVERSED' THEN 'Correction' END)",UUID.randomUUID(),"PAY"+i,charge,i*100,i==3?"REVERSED":"RECORDED",i==3?"REVERSED":"RECORDED",i==3?"REVERSED":"RECORDED");
    }
    private void dormitory() {
        UUID building=UUID.randomUUID(),room=UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_buildings(id,code,name,status) VALUES (?,'BUILDING','Building','INACTIVE')",building);
        jdbc.update("INSERT INTO dormitory_rooms(id,building_id,code,name) VALUES (?,?,'ROOM','Room')",room,building);
        for(UUID studentId:List.of(student,other)) {
            UUID bed=UUID.randomUUID(); jdbc.update("INSERT INTO dormitory_beds(id,room_id,code,name) VALUES (?,?,?,'Bed')",bed,room,bed.toString().substring(0,20));
            jdbc.update("INSERT INTO dormitory_assignments(id,student_id,bed_id,assigned_at) VALUES (?,?,?,?)",UUID.randomUUID(),studentId,bed,stamp(time));
        }
    }
    private void academic() {
        UUID course=UUID.randomUUID(),term=UUID.randomUUID(),offering=UUID.randomUUID();section=UUID.randomUUID();
        jdbc.update("INSERT INTO academic_courses(id,code,title,credits,organization_unit_id) VALUES (?,'COURSE','Course',3,?)",course,unit);
        jdbc.update("INSERT INTO academic_terms(id,code,name,start_date,end_date) VALUES (?,'TERM','Term',CURRENT_DATE,CURRENT_DATE)",term);
        jdbc.update("INSERT INTO academic_course_offerings(id,term_id,course_id,organization_unit_id) VALUES (?,?,?,?)",offering,term,course,unit);
        jdbc.update("INSERT INTO academic_class_sections(id,offering_id,code,capacity) VALUES (?,?,'SECTION',10)",section,offering);
        jdbc.update("INSERT INTO academic_enrollments(id,student_id,section_id,status,updated_at) VALUES (?,?,?,'ENROLLED',?),(?,?,?,'WITHDRAWN',?)",UUID.randomUUID(),student,section,stamp(time),UUID.randomUUID(),other,section,stamp(time));
    }
    private void event() {
        event=UUID.randomUUID();
        jdbc.update("INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity) VALUES (?,'EVENT','Event','Description',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day',10)",event);
        jdbc.update("INSERT INTO event_registrations(id,event_id,student_id,registered_at) VALUES (?,?,?,?)",UUID.randomUUID(),event,student,stamp(time));
        jdbc.update("INSERT INTO event_registrations(id,event_id,student_id,status,registered_at,attended_at) VALUES (?,?,?,'ATTENDED',?,?)",UUID.randomUUID(),event,other,stamp(time),stamp(time));
    }
    private void library() {
        title=UUID.randomUUID(); copy=UUID.randomUUID();
        jdbc.update("INSERT INTO library_titles(id,code,title,author) VALUES (?,'TITLE',?,'Author')",title,"=SUM(1,2)\r\n\"Sách\"");
        for(UUID studentId:List.of(student,other)) {
            UUID copyId=studentId.equals(student)?copy:UUID.randomUUID();
            jdbc.update("INSERT INTO library_copies(id,title_id,code) VALUES (?,?,?)",copyId,title,copyId.toString().substring(0,20));
            jdbc.update("INSERT INTO library_loans(id,copy_id,student_id,borrowed_at,due_at) VALUES (?,?,?,?,?)",UUID.randomUUID(),copyId,studentId,stamp(time),stamp(time.plusSeconds(studentId.equals(student)?100:999999999)));
        }
    }
    private java.sql.Timestamp stamp(Instant instant) { return java.sql.Timestamp.from(instant); }
    private String token(RoleCode role) { return tokens.accessToken(accounts.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Report User",passwords.encode("test-only-placeholder"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now()))); }
    private ResultActions call(MockHttpServletRequestBuilder request,String token) throws Exception { if(!token.isEmpty()) request.header("Authorization","Bearer "+token); return mvc.perform(request); }
    private JsonNode read(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
}
