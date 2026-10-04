package com.campus.notification.infrastructure.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Current-state aggregates over this module's own tables only. */
@Repository("notificationDashboard")
class OwnerDashboardAdapter implements DashboardContributor {
    private static final String SQL = "SELECT (SELECT count(*) FROM notification_notices WHERE status='PUBLISHED') AS published_notices, (SELECT count(*) FROM notification_deliveries) AS deliveries, (SELECT count(*) FROM notification_deliveries WHERE status='UNREAD') AS unread_deliveries";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerDashboardAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Owner owner() { return Owner.NOTIFICATION; }

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
