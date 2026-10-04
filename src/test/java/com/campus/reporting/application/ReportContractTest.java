package com.campus.reporting.application;

import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportContractTest {
    @Test void boundsAndUnsupportedFiltersAreRejected() {
        for(int[] range:List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101},new int[]{Integer.MAX_VALUE,100}))
            assertThatThrownBy(()->new ReportSearch(range[0],range[1],null,null,null,null,null,false,true)).isInstanceOf(IllegalArgumentException.class);
        Instant time=Instant.parse("2026-01-01T00:00:00Z");
        assertThatThrownBy(()->new ReportSearch(0,20,null,null,null,time,time,false,true)).isInstanceOf(IllegalArgumentException.class);
        for(var kind:ReportKind.values()) {
            kind.validate(query(null,null,false));
            assertThatThrownBy(()->kind.validate(query("UNKNOWN",null,false))).isInstanceOf(IllegalArgumentException.class);
            if(kind!=ReportKind.LIBRARY_LOANS) assertThatThrownBy(()->kind.validate(query(null,null,true))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->ReportKind.STUDENT_DEBT.validate(query(null,UUID.randomUUID(),false))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void csvQuotesAndNeutralizesHiddenSpreadsheetFormulasWithoutChangingJsonData() {
        for(String dangerous:List.of("=1+1","+1","-1","@SUM(1)","\t=1","\r\n=1"," \u00a0=1","\uFEFF=1"))
            assertThat(CsvReportWriter.safe(dangerous)).isEqualTo("'"+dangerous);
        assertThat(CsvReportWriter.safe("ordinary text")).isEqualTo("ordinary text");
        UUID id=UUID.randomUUID();
        var row=new ReportRow.Loan(id,id,id,id,"COPY","=SUM(1,2)\r\n\"Quoted\"",Instant.EPOCH,Instant.EPOCH.plusSeconds(1),false);
        String csv=CsvReportWriter.write(ReportKind.LIBRARY_LOANS.columns(),List.of(row));
        assertThat(csv).contains("\"'=SUM(1,2)\r\n\"\"Quoted\"\"\"").endsWith("\r\n");
        assertThat(row.title()).startsWith("=SUM");
    }
    @Test void detailServiceRejectsIncompleteRegistrations() {
        assertThatThrownBy(()->new DetailReportService(List.of())).isInstanceOf(IllegalStateException.class);
    }
    private ReportSearch query(String status,UUID resource,boolean overdue) { return new ReportSearch(0,20,null,resource,status,null,null,overdue,true); }
}
