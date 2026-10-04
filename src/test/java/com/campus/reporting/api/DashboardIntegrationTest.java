package com.campus.reporting.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.shared.application.reporting.DashboardContributor;
import com.campus.reporting.application.DashboardService;
import com.campus.identity.domain.*;
import com.campus.identity.infrastructure.security.TokenService;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @PostgresApplicationTest
@Transactional
class DashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardService service;
    @Autowired UserAccountRepository accounts;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired List<DashboardContributor> contributors;
    @MockitoSpyBean(name = "identityDashboard") DashboardContributor identity;
    static final String ROOT = "/api/v1/admin/reports/dashboard";

    @Test void adminOnlyReadContractAndEmptyOwnerMetrics() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + token(RoleCode.USER))).andExpect(status().isForbidden());
        var result = mvc.perform(get(ROOT).header("Authorization", "Bearer " + token(RoleCode.ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var view = json.readTree(result);
        assertThat(view.path("groups").size()).isEqualTo(8);
        assertThat(view.path("currency").asText()).isEqualTo("VND");
        assertThat(view.path("groups").path("IDENTITY").path("users").asLong()).isEqualTo(2);
        for (String group : List.of("PEOPLE", "ACADEMIC", "DORMITORY", "FINANCE", "NOTIFICATION", "EVENT", "LIBRARY"))
            view.path("groups").path(group).elements().forEachRemaining(value -> assertThat(value.decimalValue()).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(result).doesNotContain("password", "email", "token", "metadata");
        var spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(spec.path("paths").path(ROOT).path("get").path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(spec.path("paths").path(ROOT).size()).isEqualTo(1);
    }

    @Test void financeAggregatesDoNotMultiplyPrincipalAndExcludeReversedAndCancelledAmounts() {
        UUID student = student(), fee = UUID.randomUUID(), charge = UUID.randomUUID(), cancelled = UUID.randomUUID();
        jdbc.update("INSERT INTO finance_fee_definitions(id,code,name,amount) VALUES (?,'FEE','Fee',1000)", fee);
        jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date) VALUES (?,'CHARGE',?,?,'FEE','Fee',1000,CURRENT_DATE)", charge,student,fee);
        jdbc.update("INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date,status) VALUES (?,'CANCEL',?,?,'FEE','Fee',900,CURRENT_DATE,'CANCELLED')", cancelled,student,fee);
        payment(charge,"ONE",100,"RECORDED"); payment(charge,"TWO",200,"RECORDED"); payment(charge,"OLD",500,"REVERSED");
        var metrics = service.dashboard().groups().get("FINANCE");
        assertThat(metrics.get("open_principal_vnd")).isEqualByComparingTo("1000");
        assertThat(metrics.get("effective_paid_vnd")).isEqualByComparingTo("300");
        assertThat(metrics.get("outstanding_vnd")).isEqualByComparingTo("700");
        jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=CURRENT_TIMESTAMP,reversal_reason='Correction' WHERE receipt_number='ONE'");
        assertThat(service.dashboard().groups().get("FINANCE").get("outstanding_vnd")).isEqualByComparingTo("800");
        jdbc.update("UPDATE finance_manual_payments SET status='REVERSED',reversed_at=CURRENT_TIMESTAMP,reversal_reason='Correction' WHERE receipt_number='TWO'");
        jdbc.update("UPDATE finance_student_charges SET status='CANCELLED' WHERE id=?",charge);
        assertThat(service.dashboard().groups().get("FINANCE").get("outstanding_vnd")).isEqualByComparingTo("0");
    }

    @Test void libraryUsesStrictCommonOverdueBoundaryAndRetainsReturnedHistory() {
        UUID student=student(), title=UUID.randomUUID();
        Instant boundary=Instant.parse("2026-10-01T00:00:00Z");
        jdbc.update("INSERT INTO library_titles(id,code,title,author) VALUES (?,'BOOK','Title','Author')",title);
        for(int i=0;i<4;i++) {
            UUID copy=UUID.randomUUID();
            jdbc.update("INSERT INTO library_copies(id,title_id,code) VALUES (?,? ,?)",copy,title,"COPY"+i);
            jdbc.update("INSERT INTO library_loans(id,copy_id,student_id,borrowed_at,due_at,status,returned_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),copy,student,java.sql.Timestamp.from(boundary.minusSeconds(100)),
                    java.sql.Timestamp.from(boundary.plusSeconds(i-1)),i==3?"RETURNED":"OPEN",i==3?java.sql.Timestamp.from(boundary):null);
        }
        var owner=contributors.stream().filter(port->port.owner()==DashboardContributor.Owner.LIBRARY).findFirst().orElseThrow();
        var metrics=owner.metrics(boundary);
        assertThat(metrics.get("open_loans")).isEqualByComparingTo("3");
        assertThat(metrics.get("overdue_loans")).isEqualByComparingTo("1");
        jdbc.update("UPDATE library_titles SET status='INACTIVE'");
        assertThat(owner.metrics(boundary).get("open_loans")).isEqualByComparingTo("3");
    }

    @Test void dormitoryOccupancyAndAvailableInventoryHaveDifferentParentStatusSemantics() {
        UUID student=student(), building=UUID.randomUUID(),room=UUID.randomUUID(),occupied=UUID.randomUUID(),vacant=UUID.randomUUID();
        jdbc.update("INSERT INTO dormitory_buildings(id,code,name) VALUES (?,'BUILDING','Building')",building);
        jdbc.update("INSERT INTO dormitory_rooms(id,building_id,code,name) VALUES (?,?,'ROOM','Room')",room,building);
        jdbc.update("INSERT INTO dormitory_beds(id,room_id,code,name) VALUES (?,?,'ONE','Bed one'), (?,?,'TWO','Bed two')",occupied,room,vacant,room);
        jdbc.update("INSERT INTO dormitory_assignments(id,student_id,bed_id) VALUES (?,?,?)",UUID.randomUUID(),student,occupied);
        var metrics=service.dashboard().groups().get("DORMITORY");
        assertThat(metrics.get("occupied_beds")).isEqualByComparingTo("1");
        assertThat(metrics.get("available_beds")).isEqualByComparingTo("1");
        jdbc.update("UPDATE dormitory_buildings SET status='INACTIVE' WHERE id=?",building);
        metrics=service.dashboard().groups().get("DORMITORY");
        assertThat(metrics.get("occupied_beds")).isEqualByComparingTo("1");
        assertThat(metrics.get("available_beds")).isEqualByComparingTo("0");
        jdbc.update("UPDATE dormitory_assignments SET status='RELEASED',released_at=CURRENT_TIMESTAMP WHERE bed_id=?",occupied);
        assertThat(service.dashboard().groups().get("DORMITORY").get("occupied_beds")).isEqualByComparingTo("0");
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allOwnersShareSnapshotDespiteAConcurrentCommittedInsert() throws Exception {
        var baseline=service.dashboard();
        var read=new CountDownLatch(1); var resume=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor(); UUID inserted=UUID.randomUUID();
        doAnswer(invocation->{
            var value=invocation.callRealMethod(); read.countDown();
            if(!resume.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Snapshot timeout");
            return value;
        }).when(identity).metrics(any(Instant.class));
        try {
            var running=pool.submit(service::dashboard);
            assertThat(read.await(10,TimeUnit.SECONDS)).isTrue();
            jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,?,'Concurrent Unit','FACULTY')",inserted,"U"+inserted.toString().substring(0,12));
            resume.countDown();
            var snapshot=running.get(15,TimeUnit.SECONDS);
            assertThat(snapshot.groups().get("PEOPLE").get("organization_units")).isEqualByComparingTo(baseline.groups().get("PEOPLE").get("organization_units"));
            assertThat(service.dashboard().groups().get("PEOPLE").get("organization_units"))
                    .isEqualByComparingTo(baseline.groups().get("PEOPLE").get("organization_units").add(BigDecimal.ONE));
        } finally {
            resume.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM organization_units WHERE id=?",inserted);
        }
    }

    @Test void peopleAcademicEventAndNotificationCountActualLifecycleStates() {
        UUID student=student(), unit=jdbc.queryForObject("SELECT organization_unit_id FROM students WHERE id=?",UUID.class,student);
        UUID faculty=UUID.randomUUID(),course=UUID.randomUUID(),term=UUID.randomUUID(),offering=UUID.randomUUID(),section=UUID.randomUUID();
        jdbc.update("INSERT INTO faculty_staff(id,personnel_number,full_name,personnel_type,organization_unit_id) VALUES (?,'FACULTY','Faculty','FACULTY',?)",faculty,unit);
        jdbc.update("INSERT INTO academic_programs(id,code,name,organization_unit_id) VALUES (?,'PROGRAM','Program',?)",UUID.randomUUID(),unit);
        jdbc.update("INSERT INTO academic_courses(id,code,title,credits,organization_unit_id) VALUES (?,'COURSE','Course',3,?)",course,unit);
        jdbc.update("INSERT INTO academic_terms(id,code,name,start_date,end_date,status) VALUES (?,'TERM','Term',CURRENT_DATE,CURRENT_DATE,'ACTIVE')",term);
        jdbc.update("INSERT INTO academic_course_offerings(id,term_id,course_id,organization_unit_id,status) VALUES (?,?,?,?,'OPEN')",offering,term,course,unit);
        jdbc.update("INSERT INTO academic_class_sections(id,offering_id,code,capacity,faculty_id,status) VALUES (?,?,'SECTION',10,?,'OPEN')",section,offering,faculty);
        jdbc.update("INSERT INTO academic_enrollments(id,section_id,student_id) VALUES (?,?,?)",UUID.randomUUID(),section,student);
        UUID event=UUID.randomUUID(),membership=UUID.randomUUID();
        jdbc.update("INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity,status) VALUES (?,'EVENT','Event','Description',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day',10,'OPEN')",event);
        jdbc.update("INSERT INTO event_registrations(id,event_id,student_id) VALUES (?,?,?)",membership,event,student);
        token(RoleCode.USER);
        UUID recipient=jdbc.queryForObject("SELECT id FROM identity_users LIMIT 1",UUID.class),template=UUID.randomUUID(),notice=UUID.randomUUID();
        jdbc.update("INSERT INTO notification_templates(id,code,name,title,body) VALUES (?,'TEMPLATE','Template','Title','Body')",template);
        jdbc.update("INSERT INTO notification_notices(id,template_id,title,body,status,published_at) VALUES (?,?,'Title','Body','PUBLISHED',CURRENT_TIMESTAMP)",notice,template);
        jdbc.update("INSERT INTO notification_deliveries(id,notice_id,recipient_id) VALUES (?,?,?)",UUID.randomUUID(),notice,recipient);
        var groups=service.dashboard().groups();
        assertThat(groups.get("PEOPLE").get("active_students")).isEqualByComparingTo("1");
        assertThat(groups.get("PEOPLE").get("active_faculty")).isEqualByComparingTo("1");
        assertThat(groups.get("ACADEMIC").values()).allSatisfy(value->assertThat(value).isEqualByComparingTo("1"));
        assertThat(groups.get("EVENT").get("registered_memberships")).isEqualByComparingTo("1");
        assertThat(groups.get("NOTIFICATION").values()).allSatisfy(value->assertThat(value).isEqualByComparingTo("1"));
        jdbc.update("UPDATE event_registrations SET status='ATTENDED',attended_at=CURRENT_TIMESTAMP WHERE id=?",membership);
        jdbc.update("UPDATE notification_deliveries SET status='READ',read_at=CURRENT_TIMESTAMP");
        jdbc.update("UPDATE faculty_staff SET status='INACTIVE'");
        jdbc.update("UPDATE students SET status='INACTIVE'");
        groups=service.dashboard().groups();
        assertThat(groups.get("EVENT").get("registered_memberships")).isEqualByComparingTo("0");
        assertThat(groups.get("EVENT").get("attended_memberships")).isEqualByComparingTo("1");
        assertThat(groups.get("NOTIFICATION").get("unread_deliveries")).isEqualByComparingTo("0");
        assertThat(groups.get("PEOPLE").get("active_faculty")).isEqualByComparingTo("0");
        assertThat(groups.get("PEOPLE").get("active_students")).isEqualByComparingTo("0");
        assertThat(groups.get("ACADEMIC").get("enrolled_memberships")).isEqualByComparingTo("1");
    }

    private void payment(UUID charge,String number,long amount,String status) {
        jdbc.update("INSERT INTO finance_manual_payments(id,receipt_number,charge_id,amount,status,reversed_at,reversal_reason) VALUES (?,?,?,?,?,CASE WHEN ?='REVERSED' THEN CURRENT_TIMESTAMP END,CASE WHEN ?='REVERSED' THEN 'Correction' END)",UUID.randomUUID(),number,charge,amount,status,status,status);
    }
    private UUID student() {
        UUID unit=UUID.randomUUID(),student=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,'UNIT','Unit','FACULTY')",unit);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) VALUES (?,'STUDENT','Student',?)",student,unit);
        return student;
    }
    private String token(RoleCode role) {
        var account=accounts.save(UserAccount.create(UUID.randomUUID(),UUID.randomUUID()+"@campus.example","Report User",passwords.encode("test-only-placeholder"),AccountStatus.ACTIVE,Set.of(roles.findByCode(role).orElseThrow()),Instant.now()));
        return tokens.accessToken(account);
    }
}
