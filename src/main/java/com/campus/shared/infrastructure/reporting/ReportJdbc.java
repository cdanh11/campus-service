package com.campus.shared.infrastructure.reporting;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.PageResult;
import com.campus.shared.application.reporting.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** SQL mechanics only: owners supply their own fixed SQL/projection; no table knowledge here. */
public final class ReportJdbc {
    private ReportJdbc() { }
    public static PageResult<ReportRow> read(NamedParameterJdbcTemplate jdbc, ReportKind kind, ReportSearch search, Instant asOf,
            boolean export, String projection, String studentColumn, String resourceColumn, String statusColumn,
            String timeColumn, String idColumn, RowMapper<ReportRow> mapper) {
        kind.validate(search);
        var filters=new ArrayList<String>(); var parameters=new HashMap<String,Object>();
        parameters.put("asOf",Timestamp.from(asOf));
        parameters.put("rangeFrom",search.from()==null?null:Timestamp.from(search.from()));
        parameters.put("rangeUntil",search.until()==null?null:Timestamp.from(search.until()));
        if(search.studentId()!=null) { filters.add(studentColumn+"=:student"); parameters.put("student",search.studentId()); }
        if(search.resourceId()!=null) { filters.add(resourceColumn+"=:resource"); parameters.put("resource",search.resourceId()); }
        if(search.status()!=null) { filters.add(statusColumn+"=:status"); parameters.put("status",search.status()); }
        if(timeColumn!=null && search.from()!=null) { filters.add(timeColumn+">=:from"); parameters.put("from",Timestamp.from(search.from())); }
        if(timeColumn!=null && search.until()!=null) { filters.add(timeColumn+"<:until"); parameters.put("until",Timestamp.from(search.until())); }
        if(search.overdueOnly()) filters.add("due_at<:asOf");
        String filtered="SELECT * FROM ("+projection+") report"+(filters.isEmpty()?"":" WHERE "+String.join(" AND ",filters));
        long total=jdbc.queryForObject("SELECT count(*) FROM ("+filtered+") counted",parameters,Long.class);
        if(export && total>5000) throw new ReportQueryPort.ExportLimitException();
        parameters.put("limit",export?5000:search.size()); parameters.put("offset",export?0:(long)search.page()*search.size());
        return new PageResult<>(jdbc.query(filtered+" ORDER BY "+idColumn+(search.ascending()?" ASC":" DESC")+" LIMIT :limit OFFSET :offset",parameters,mapper),total);
    }
}
