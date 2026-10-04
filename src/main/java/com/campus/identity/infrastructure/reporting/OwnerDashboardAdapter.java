package com.campus.identity.infrastructure.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Current-state aggregates over this module's own tables only. */
@Repository("identityDashboard")
class OwnerDashboardAdapter implements DashboardContributor {
    private static final String SQL = "SELECT count(*) AS users, count(*) FILTER (WHERE status='ACTIVE') AS active_users FROM identity_users";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerDashboardAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Owner owner() { return Owner.IDENTITY; }

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
