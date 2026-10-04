package com.campus.event.infrastructure.reporting;

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
@Repository("eventReport")
class OwnerReportAdapter implements ReportQueryPort {
    private static final String SQL = "SELECT r.id,r.event_id,r.student_id,e.code AS event_code,e.title AS event_title,r.status,r.registered_at,r.cancelled_at,r.attended_at FROM event_registrations r JOIN campus_events e ON e.id=r.event_id";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerReportAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportKind kind() { return ReportKind.EVENT_MEMBERSHIP; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<ReportRow> read(ReportSearch search,Instant asOf,boolean export) {
        return ReportJdbc.read(jdbc,kind(),search,asOf,export,SQL,"student_id","event_id","status","registered_at","id",
                (row,index)->new ReportRow.Membership(uuid(row,"id"),uuid(row,"event_id"),uuid(row,"student_id"),row.getString("event_code"),row.getString("event_title"),row.getString("status"),instant(row,"registered_at"),instant(row,"cancelled_at"),instant(row,"attended_at")));
    }
    private static UUID uuid(ResultSet row,String column) throws SQLException { return row.getObject(column,UUID.class); }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
