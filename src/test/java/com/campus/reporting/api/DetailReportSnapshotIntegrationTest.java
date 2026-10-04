package com.campus.reporting.api;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.campus.reporting.application.DetailReportService;
import com.campus.shared.application.reporting.*;
import com.campus.testsupport.PostgresApplicationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @ActiveProfiles("test") @PostgresApplicationTest
class DetailReportSnapshotIntegrationTest {
    @Autowired DetailReportService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    @Test void concurrentCommitCannotChangePageOrExportAfterCountSnapshot() throws Exception {
        UUID unit=UUID.randomUUID(),student=UUID.randomUUID(),title=UUID.randomUUID();
        jdbc.update("INSERT INTO organization_units(id,code,name,unit_type) VALUES (?,'UNIT','Unit','FACULTY')",unit);
        jdbc.update("INSERT INTO students(id,student_number,full_name,organization_unit_id) VALUES (?,'STUDENT','Student',?)",student,unit);
        jdbc.update("INSERT INTO library_titles(id,code,title,author) VALUES (?,'TITLE','Title','Author')",title);
        var search=new ReportSearch(0,100,student,null,null,null,null,false,true);
        var pool=Executors.newSingleThreadExecutor();
        try {
            for(boolean export:List.of(false,true)) {
                UUID initial=addLoan(student,title);
                long baseline=service.search(ReportKind.LIBRARY_LOANS,search).totalElements();
                var counted=new CountDownLatch(1);var resume=new CountDownLatch(1);
                doAnswer(invocation->{
                    Object result=invocation.callRealMethod();
                    if(((String)invocation.getArgument(0)).contains("library_loans")) {
                        counted.countDown(); if(!resume.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Snapshot timeout");
                    }
                    return result;
                }).when(named).queryForObject(anyString(),anyMap(),eq(Long.class));
                try {
                    var running=pool.submit(()->export?service.export(ReportKind.LIBRARY_LOANS,search):service.search(ReportKind.LIBRARY_LOANS,search));
                    assertThat(counted.await(10,TimeUnit.SECONDS)).isTrue();
                    UUID appended=addLoan(student,title); resume.countDown();
                    Object result=running.get(15,TimeUnit.SECONDS);
                    if(export) assertThat((String)result).contains(initial.toString()).doesNotContain(appended.toString());
                    else {
                        var page=(DetailReportService.ReportPage)result;
                        assertThat(page.totalElements()).isEqualTo(baseline);
                        assertThat(page.content()).hasSize((int)baseline);
                        assertThat(page.content().stream().map(row->((ReportRow.Loan)row).id())).contains(initial).doesNotContain(appended);
                    }
                    assertThat(service.search(ReportKind.LIBRARY_LOANS,search).totalElements()).isEqualTo(baseline+1);
                } finally { resume.countDown(); reset(named); }
            }
        } finally {
            pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM library_loans WHERE student_id=?",student);
            jdbc.update("DELETE FROM library_copies WHERE title_id=?",title);
            jdbc.update("DELETE FROM library_titles WHERE id=?",title);
            jdbc.update("DELETE FROM students WHERE id=?",student);
            jdbc.update("DELETE FROM organization_units WHERE id=?",unit);
        }
    }
    private UUID addLoan(UUID student,UUID title) {
        UUID copy=UUID.randomUUID(),loan=UUID.randomUUID();
        jdbc.update("INSERT INTO library_copies(id,title_id,code) VALUES (?,?,?)",copy,title,copy.toString().substring(0,20));
        jdbc.update("INSERT INTO library_loans(id,copy_id,student_id) VALUES (?,?,?)",loan,copy,student);
        return loan;
    }
}
