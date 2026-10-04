package com.campus.library.infrastructure.persistence;

import java.util.UUID;
import com.campus.library.application.LibraryService;
import com.campus.library.domain.*;
import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Unforced selective plan evidence; no production latency or load benchmark claim. */
@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class LibraryQueryPlanIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired LibraryService service;
    @Autowired LibraryRepository repository;
    @Test void selectiveCatalogAvailabilityAndStudentPagesAgreeWithProductionQueries() throws Exception {
        String prefix=UUID.randomUUID().toString().substring(0,8);
        jdbc.update("""
            INSERT INTO library_titles(id,code,title,author,status)
            SELECT gen_random_uuid(),?||i,'Query title '||i,'Author',CASE WHEN i<=10 THEN 'INACTIVE' ELSE 'ACTIVE' END
            FROM generate_series(1,10000) i
            """,prefix);
        jdbc.execute("ANALYZE library_titles");
        var titlePlan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM library_titles WHERE status='INACTIVE' ORDER BY code,id LIMIT 5",String.class));
        assertThat(titlePlan.findValuesAsText("Index Name")).contains("ix_library_title_status_code");
        var expected=jdbc.queryForList("SELECT id FROM library_titles WHERE status='INACTIVE' ORDER BY code,id LIMIT 5",UUID.class);
        var page=service.titles(new LibrarySearch(LibrarySearch.Resource.TITLE,0,5,null,null,null,null,"INACTIVE","code",true));
        assertThat(page.totalElements()).isEqualTo(10); assertThat(page.content().stream().map(BookTitle::id).toList()).isEqualTo(expected);
        UUID target=expected.get(0),other=expected.get(1),unit=UUID.randomUUID(),student=UUID.randomUUID(),otherStudent=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,?,'Library query unit','FACULTY')",unit,prefix);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) VALUES (?,?,'Query Student',?),(?,?,'Other Student',?)",student,"S"+prefix,unit,otherStudent,"T"+prefix,unit);
        jdbc.update("""
            INSERT INTO library_copies(id,title_id,code,status)
            SELECT gen_random_uuid(),CASE WHEN i<=10 THEN ?::uuid ELSE ?::uuid END,?||i,'ACTIVE'
            FROM generate_series(1,10000) i
            """,target,other,prefix);
        jdbc.execute("ANALYZE library_copies");
        var copyPlan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM library_copies WHERE title_id=? AND status='ACTIVE' ORDER BY code,id LIMIT 5",String.class,target));
        assertThat(copyPlan.findValuesAsText("Index Name")).contains("ix_library_copy_title_status");
        var copies=service.copies(new LibrarySearch(LibrarySearch.Resource.COPY,0,5,null,target,null,null,"ACTIVE","code",true));
        assertThat(copies.totalElements()).isEqualTo(10);
        assertThat(copies.content().stream().map(BookCopy::id).toList()).isEqualTo(jdbc.queryForList("SELECT id FROM library_copies WHERE title_id=? ORDER BY code,id LIMIT 5",UUID.class,target));
        jdbc.update("""
            INSERT INTO library_loans(id,copy_id,student_id,status,borrowed_at,due_at,returned_at)
            SELECT gen_random_uuid(),id,CASE WHEN title_id=? THEN ?::uuid ELSE ?::uuid END,
                CASE WHEN title_id=? THEN 'OPEN' ELSE 'RETURNED' END,
                TIMESTAMPTZ '2026-01-01 00:00:00Z',TIMESTAMPTZ '2026-01-15 00:00:00Z',
                CASE WHEN title_id=? THEN NULL ELSE TIMESTAMPTZ '2026-01-02 00:00:00Z' END
            FROM library_copies WHERE title_id IN (?,?)
            """,target,student,otherStudent,target,target,target,other);
        jdbc.execute("ANALYZE library_loans");
        UUID copy=copies.content().get(0).id();
        var availability=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT id FROM library_loans WHERE copy_id=? AND status='OPEN' LIMIT 1",String.class,copy));
        assertThat(availability.findValuesAsText("Index Name")).contains("ux_library_open_copy"); assertThat(repository.hasOpenLoan(copy)).isTrue();
        var studentPlan=json.readTree(jdbc.queryForObject("EXPLAIN (FORMAT JSON) SELECT * FROM library_loans WHERE student_id=? AND status='OPEN' ORDER BY due_at,id LIMIT 5",String.class,student));
        assertThat(studentPlan.findValuesAsText("Index Name")).containsAnyOf("ix_library_loan_student_status","ix_library_loan_status_due");
        assertThat(studentPlan.toString()).contains("student_id","OPEN");
        var loans=service.loans(new LibrarySearch(LibrarySearch.Resource.LOAN,0,5,null,null,null,student,"OPEN","dueAt",true));
        assertThat(loans.totalElements()).isEqualTo(10);
        assertThat(loans.content().stream().map(BookLoan::id).toList()).isEqualTo(jdbc.queryForList("SELECT id FROM library_loans WHERE student_id=? AND status='OPEN' ORDER BY due_at,id LIMIT 5",UUID.class,student));
    }
}
