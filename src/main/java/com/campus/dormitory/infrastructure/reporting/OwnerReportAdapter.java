package com.campus.dormitory.infrastructure.reporting;

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
@Repository("dormitoryReport")
class OwnerReportAdapter implements ReportQueryPort {
    private static final String SQL = "SELECT a.id,a.student_id,a.bed_id,b.room_id,r.building_id,a.assigned_at FROM dormitory_assignments a JOIN dormitory_beds b ON b.id=a.bed_id JOIN dormitory_rooms r ON r.id=b.room_id WHERE a.status='ASSIGNED'";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerReportAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportKind kind() { return ReportKind.CURRENT_ACCOMMODATION; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public PageResult<ReportRow> read(ReportSearch search,Instant asOf,boolean export) {
        return ReportJdbc.read(jdbc,kind(),search,asOf,export,SQL,"student_id","bed_id",null,"assigned_at","id",
                (row,index)->new ReportRow.Accommodation(uuid(row,"id"),uuid(row,"student_id"),uuid(row,"bed_id"),uuid(row,"room_id"),uuid(row,"building_id"),instant(row,"assigned_at")));
    }
    private static UUID uuid(ResultSet row,String column) throws SQLException { return row.getObject(column,UUID.class); }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getTimestamp(column); return value==null?null:value.toInstant();
    }
}
