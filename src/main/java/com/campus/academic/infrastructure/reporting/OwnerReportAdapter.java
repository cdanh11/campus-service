package com.campus.academic.infrastructure.reporting;

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
@Repository("academicReport")
class OwnerReportAdapter implements ReportQueryPort {
    private static final String SQL = "SELECT e.id,e.student_id,e.section_id,s.offering_id,o.term_id,o.course_id,e.status,e.updated_at FROM academic_enrollments e JOIN academic_class_sections s ON s.id=e.section_id JOIN academic_course_offerings o ON o.id=s.offering_id";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerReportAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportKind kind() { return ReportKind.SECTION_ENROLLMENT; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<ReportRow> read(ReportSearch search,Instant asOf,boolean export) {
        return ReportJdbc.read(jdbc,kind(),search,asOf,export,SQL,"student_id","section_id","status","updated_at","id",
                (row,index)->new ReportRow.Enrollment(uuid(row,"id"),uuid(row,"student_id"),uuid(row,"section_id"),uuid(row,"offering_id"),uuid(row,"term_id"),uuid(row,"course_id"),row.getString("status"),instant(row,"updated_at")));
    }
    private static UUID uuid(ResultSet row,String column) throws SQLException { return row.getObject(column,UUID.class); }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
