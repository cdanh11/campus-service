package com.campus.library.infrastructure.reporting;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import com.campus.shared.application.PageResult;
import com.campus.shared.application.reporting.*;
import com.campus.shared.infrastructure.reporting.ReportJdbc;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Fixed safe projection over this owner's data only; foreign references remain UUIDs. */
@Repository("libraryReport")
class OwnerReportAdapter implements ReportQueryPort {
    private static final String SQL = "SELECT l.id,l.student_id,l.copy_id,c.title_id,c.code AS copy_code,t.title,l.borrowed_at,l.due_at,(l.due_at<:asOf) AS overdue FROM library_loans l JOIN library_copies c ON c.id=l.copy_id JOIN library_titles t ON t.id=c.title_id WHERE l.status='OPEN'";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerReportAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportKind kind() { return ReportKind.LIBRARY_LOANS; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<ReportRow> read(ReportSearch search,Instant asOf,boolean export) {
        return ReportJdbc.read(jdbc,kind(),search,asOf,export,SQL,"student_id","copy_id",null,"borrowed_at","id",
                (row,index)->new ReportRow.Loan(uuid(row,"id"),uuid(row,"student_id"),uuid(row,"copy_id"),uuid(row,"title_id"),row.getString("copy_code"),row.getString("title"),instant(row,"borrowed_at"),instant(row,"due_at"),row.getBoolean("overdue")));
    }
    private static UUID uuid(ResultSet row,String column) throws SQLException { return row.getObject(column,UUID.class); }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
