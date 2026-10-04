package com.campus.finance.infrastructure.reporting;

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
@Repository("financeReport")
class OwnerReportAdapter implements ReportQueryPort {
    private static final String SQL = "SELECT c.student_id,count(*) AS charge_count,sum(c.amount) AS principal_vnd,sum(COALESCE(p.paid,0)) AS paid_vnd,sum(c.amount-COALESCE(p.paid,0)) AS outstanding_vnd FROM finance_student_charges c LEFT JOIN LATERAL (SELECT sum(amount) AS paid FROM finance_manual_payments WHERE status='RECORDED' AND charge_id=c.id) p ON true WHERE c.status='OPEN' AND (CAST(:rangeFrom AS timestamptz) IS NULL OR c.created_at>=:rangeFrom) AND (CAST(:rangeUntil AS timestamptz) IS NULL OR c.created_at<:rangeUntil) GROUP BY c.student_id";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerReportAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportKind kind() { return ReportKind.STUDENT_DEBT; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<ReportRow> read(ReportSearch search,Instant asOf,boolean export) {
        return ReportJdbc.read(jdbc,kind(),search,asOf,export,SQL,"student_id",null,null,null,"student_id",
                (row,index)->new ReportRow.Debt(uuid(row,"student_id"),row.getLong("charge_count"),row.getBigDecimal("principal_vnd"),row.getBigDecimal("paid_vnd"),row.getBigDecimal("outstanding_vnd")));
    }
    private static UUID uuid(ResultSet row,String column) throws SQLException { return row.getObject(column,UUID.class); }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
