package com.campus.operations;

import java.math.BigDecimal;
import java.util.UUID;
import com.campus.finance.application.ManualPaymentService;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsQueryPlanIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ManualPaymentService payments;
    UUID student, building, room, bed, fee, charge;

    @BeforeAll void seedSyntheticQueryFixture() {
        UUID unit=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,'QUERY','Query Unit','FACULTY')",unit);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) SELECT gen_random_uuid(),'QST'||n,'Query Student',? FROM generate_series(1,100) n",unit);
        jdbc.execute("INSERT INTO dormitory_buildings(id,code,name) SELECT gen_random_uuid(),'QB'||n,'Query Building' FROM generate_series(1,100) n");
        jdbc.execute("""
                INSERT INTO dormitory_rooms(id,building_id,code,name)
                SELECT gen_random_uuid(),b.id,'QR'||n,'Query Room' FROM dormitory_buildings b CROSS JOIN generate_series(1,100) n
                """);
        jdbc.execute("INSERT INTO dormitory_beds(id,room_id,code,name) SELECT gen_random_uuid(),r.id,'BED','Query Bed' FROM dormitory_rooms r");
        jdbc.execute("""
                INSERT INTO dormitory_assignments(id,student_id,bed_id,status,released_at)
                SELECT gen_random_uuid(),s.id,d.id,'RELEASED',CURRENT_TIMESTAMP FROM students s
                CROSS JOIN (SELECT d.id FROM dormitory_beds d JOIN dormitory_rooms r ON r.id=d.room_id
                            JOIN dormitory_buildings b ON b.id=r.building_id WHERE b.code='QB1') d
                """);
        jdbc.execute("""
                INSERT INTO dormitory_assignments(id,student_id,bed_id)
                SELECT gen_random_uuid(),s.id,d.id FROM students s JOIN dormitory_rooms r ON r.code='QR'||substring(s.student_number from 4)
                JOIN dormitory_buildings b ON b.id=r.building_id AND b.code='QB1' JOIN dormitory_beds d ON d.room_id=r.id
                """);
        jdbc.execute("INSERT INTO finance_fee_definitions(id,code,name,amount) SELECT gen_random_uuid(),'QFE'||n,'Query Fee',100 FROM generate_series(1,100) n");
        jdbc.execute("""
                INSERT INTO finance_student_charges(id,charge_number,student_id,fee_id,fee_code,fee_name,amount,due_date)
                SELECT gen_random_uuid(),s.student_number||'-'||f.code,s.id,f.id,f.code,f.name,f.amount,DATE '2026-01-01'
                FROM students s CROSS JOIN finance_fee_definitions f
                """);
        jdbc.execute("""
                INSERT INTO finance_manual_payments(id,receipt_number,charge_id,amount,status,reversed_at,reversal_reason)
                SELECT gen_random_uuid(),c.charge_number||'-P'||n,c.id,10,
                    CASE WHEN n=5 THEN 'REVERSED' ELSE 'RECORDED' END,
                    CASE WHEN n=5 THEN CURRENT_TIMESTAMP ELSE NULL END,
                    CASE WHEN n=5 THEN 'Synthetic correction' ELSE NULL END
                FROM finance_student_charges c CROSS JOIN generate_series(1,5) n
                """);
        for(String table:new String[]{"dormitory_rooms","dormitory_beds","dormitory_assignments","finance_student_charges","finance_manual_payments"})
            jdbc.execute("ANALYZE "+table);
        student=jdbc.queryForObject("SELECT id FROM students WHERE student_number='QST1'",UUID.class);
        building=jdbc.queryForObject("SELECT id FROM dormitory_buildings WHERE code='QB1'",UUID.class);
        room=jdbc.queryForObject("SELECT id FROM dormitory_rooms WHERE building_id=? AND code='QR1'",UUID.class,building);
        bed=jdbc.queryForObject("SELECT id FROM dormitory_beds WHERE room_id=?",UUID.class,room);
        fee=jdbc.queryForObject("SELECT id FROM finance_fee_definitions WHERE code='QFE1'",UUID.class);
        charge=jdbc.queryForObject("SELECT id FROM finance_student_charges WHERE student_id=? AND fee_id=?",UUID.class,student,fee);
    }

    @Test void selectiveInventoryParentQueriesUseExistingIndexes() throws Exception {
        var rooms=plan("SELECT count(*) FROM dormitory_rooms WHERE building_id=? AND status='ACTIVE'",building);
        // Both indexes begin with the parent key; the planner may prefer the narrower unique one.
        assertThat(rooms.findValuesAsText("Index Name")).containsAnyOf("ix_dormitory_rooms_status", "ux_dormitory_rooms_code");
        var beds=plan("SELECT count(*) FROM dormitory_beds WHERE room_id=? AND status='ACTIVE'",room);
        assertThat(beds.findValuesAsText("Index Name")).containsAnyOf("ix_dormitory_beds_status", "ux_dormitory_beds_code");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_rooms WHERE building_id=? AND status='ACTIVE'",Long.class,building)).isEqualTo(100);
    }

    @Test void currentAndHistoricalAssignmentsUseTheirSelectiveIndexes() throws Exception {
        var occupied=plan("SELECT count(*) FROM dormitory_assignments WHERE bed_id=? AND status='ASSIGNED'",bed);
        assertThat(occupied.findValuesAsText("Index Name")).contains("ux_dormitory_assignment_current_bed");
        var current=plan("SELECT count(*) FROM dormitory_assignments WHERE student_id=? AND status='ASSIGNED'",student);
        assertThat(current.findValuesAsText("Index Name")).contains("ux_dormitory_assignment_current_student");
        var historical=plan("SELECT * FROM dormitory_assignments WHERE student_id=? AND status='RELEASED'",student);
        assertThat(historical.findValuesAsText("Index Name")).contains("ix_dormitory_assignment_student_status");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_assignments WHERE student_id=? AND status='ASSIGNED'",Long.class,student)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dormitory_assignments WHERE student_id=? AND status='RELEASED'",Long.class,student)).isEqualTo(100);
    }

    @Test void selectiveChargeAndBalanceQueriesUseExistingIndexesAndAgreeWithProductionProjection() throws Exception {
        var studentCharges=plan("SELECT * FROM finance_student_charges WHERE student_id=? AND status='OPEN'",student);
        assertThat(studentCharges.findValuesAsText("Index Name")).contains("ix_finance_charge_student_status");
        var feeCharges=plan("SELECT * FROM finance_student_charges WHERE fee_id=? AND status='OPEN'",fee);
        assertThat(feeCharges.findValuesAsText("Index Name")).contains("ix_finance_charge_fee_status");
        var totals=plan("SELECT coalesce(sum(amount),0) FROM finance_manual_payments WHERE charge_id=? AND status='RECORDED'",charge);
        assertThat(totals.findValuesAsText("Index Name")).contains("ix_finance_payment_charge_status");
        var balancePlan=plan("""
                SELECT c.id,c.amount,c.currency,c.status,c.row_version,coalesce(sum(p.amount),0)
                FROM finance_student_charges c LEFT JOIN finance_manual_payments p ON p.charge_id=c.id AND p.status='RECORDED'
                WHERE c.id=? GROUP BY c.id,c.amount,c.currency,c.status,c.row_version
                """,charge);
        assertThat(balancePlan.findValuesAsText("Index Name")).contains("finance_student_charges_pkey","ix_finance_payment_charge_status");
        var balance=payments.balance(charge);
        assertThat(balance.paidAmount()).isEqualTo(new BigDecimal("40"));
        assertThat(balance.outstandingAmount()).isEqualTo(new BigDecimal("60"));
    }

    private JsonNode plan(String sql,Object... parameters) throws Exception {
        // Default PostgreSQL planner; no enable_seqscan overrides or production latency claim.
        return json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) "+sql,String.class,parameters));
    }
}
