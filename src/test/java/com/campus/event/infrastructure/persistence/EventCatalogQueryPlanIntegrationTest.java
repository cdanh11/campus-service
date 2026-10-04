package com.campus.event.infrastructure.persistence;

import java.util.UUID;
import com.campus.event.application.EventCatalogService;
import com.campus.event.application.EventRegistrationService;
import com.campus.event.domain.*;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Selective plan evidence, not a latency/load benchmark; no forced planner settings. */
@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class EventCatalogQueryPlanIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired EventCatalogService catalog;
    @Autowired EventRegistrationService registrations;
    @Autowired EventRepository repository;
    @Test void selectiveStatusPageUsesExistingIndexAndAgreesWithProductionQuery() throws Exception {
        String prefix=UUID.randomUUID().toString().substring(0,8);
        jdbc.update("""
            INSERT INTO campus_events(id,code,title,description,starts_at,ends_at,capacity,status)
            SELECT gen_random_uuid(),?||i,'Query title '||i,'Query description',
                TIMESTAMPTZ '2026-12-01 08:00:00Z'+i*interval '1 minute',
                TIMESTAMPTZ '2026-12-01 10:00:00Z'+i*interval '1 minute',10,
                CASE WHEN i<=10 THEN 'OPEN' ELSE 'DRAFT' END
            FROM generate_series(1,10000) i
            """,prefix);
        jdbc.execute("ANALYZE campus_events");
        var plan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM campus_events WHERE status=? ORDER BY starts_at,id LIMIT 5",String.class,"OPEN"));
        assertThat(plan.findValuesAsText("Index Name")).contains("ix_event_status_start");
        var expected=jdbc.queryForList("SELECT id FROM campus_events WHERE status='OPEN' ORDER BY starts_at,id LIMIT 5",UUID.class);
        var result=catalog.search(new EventSearch(0,5,null,CampusEvent.Status.OPEN,"startsAt",true));
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.content().stream().map(CampusEvent::id).toList()).isEqualTo(expected);
        UUID unit=UUID.randomUUID(),target=expected.get(0),other=expected.get(1);
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,?,'Query unit','FACULTY')",unit,prefix);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) SELECT gen_random_uuid(),?||i,'Query Student '||i,? FROM generate_series(1,10000) i","S"+prefix,unit);
        jdbc.update("UPDATE campus_events SET capacity=10000 WHERE id=?",other);
        jdbc.update("""
            INSERT INTO event_registrations(id,event_id,student_id)
            SELECT gen_random_uuid(),CASE WHEN ordinal<=10 THEN ?::uuid ELSE ?::uuid END,id
            FROM (SELECT id,row_number() OVER (ORDER BY id) ordinal FROM students WHERE organization_unit_id=?) eligible
            """,target,other,unit);
        jdbc.execute("ANALYZE event_registrations");
        var capacityPlan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT count(*) FROM event_registrations WHERE event_id=? AND status IN ('REGISTERED','ATTENDED')",String.class,target));
        assertThat(capacityPlan.findValuesAsText("Index Name")).containsAnyOf("ix_event_registration_event_status","ux_event_registration_membership");
        assertThat(capacityPlan.toString()).contains("event_id","REGISTERED","ATTENDED");
        assertThat(repository.consumedSeats(target)).isEqualTo(10);
        UUID student=jdbc.queryForObject("SELECT student_id FROM event_registrations WHERE event_id=? ORDER BY id LIMIT 1",UUID.class,target);
        var studentPlan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM event_registrations WHERE student_id=? AND status='REGISTERED' ORDER BY registered_at,id LIMIT 5",String.class,student));
        assertThat(studentPlan.findValuesAsText("Index Name")).contains("ix_event_registration_student_status");
        var studentPage=registrations.search(new RegistrationSearch(0,5,null,student,EventRegistration.Status.REGISTERED,"registeredAt",true));
        assertThat(studentPage.totalElements()).isEqualTo(1); assertThat(studentPage.content()).singleElement().satisfies(value->assertThat(value.studentId()).isEqualTo(student));
    }
}
