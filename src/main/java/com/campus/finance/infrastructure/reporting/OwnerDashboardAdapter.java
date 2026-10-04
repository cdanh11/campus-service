package com.campus.finance.infrastructure.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Current-state aggregates over this module's own tables only. */
@Repository("financeDashboard")
class OwnerDashboardAdapter implements DashboardContributor {
    private static final String SQL = "WITH balances AS (SELECT c.status,c.amount,COALESCE(p.paid,0) AS paid FROM finance_student_charges c LEFT JOIN (SELECT charge_id,sum(amount) AS paid FROM finance_manual_payments WHERE status='RECORDED' GROUP BY charge_id) p ON p.charge_id=c.id) SELECT count(*) AS charges, count(*) FILTER (WHERE status='OPEN') AS open_charges, COALESCE(sum(amount) FILTER (WHERE status='OPEN'),0) AS open_principal_vnd, COALESCE(sum(paid),0) AS effective_paid_vnd, COALESCE(sum(amount-paid) FILTER (WHERE status='OPEN'),0) AS outstanding_vnd FROM balances";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerDashboardAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Owner owner() { return Owner.FINANCE; }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> metrics(Instant asOf) {
        return jdbc.queryForObject(SQL, Map.of("asOf", Timestamp.from(asOf)), (row, index) -> {
            var metrics = new LinkedHashMap<String, BigDecimal>();
            var columns = row.getMetaData();
            for (int column = 1; column <= columns.getColumnCount(); column++)
                metrics.put(columns.getColumnLabel(column), row.getBigDecimal(column));
            return Collections.unmodifiableMap(metrics);
        });
    }
}
