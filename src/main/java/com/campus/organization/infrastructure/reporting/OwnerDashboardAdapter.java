package com.campus.organization.infrastructure.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Current-state aggregates over this module's own tables only. */
@Repository("organizationDashboard")
class OwnerDashboardAdapter implements DashboardContributor {
    private static final String SQL = "SELECT count(*) AS organization_units, count(*) FILTER (WHERE status='ACTIVE') AS active_organization_units FROM organization_units";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerDashboardAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Owner owner() { return Owner.ORGANIZATION; }

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
