package com.campus.dormitory.infrastructure.reporting;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Current-state aggregates over this module's own tables only. */
@Repository("dormitoryDashboard")
class OwnerDashboardAdapter implements DashboardContributor {
    private static final String SQL = "SELECT (SELECT count(*) FROM dormitory_beds) AS beds, (SELECT count(*) FROM dormitory_assignments WHERE status='ASSIGNED') AS occupied_beds, (SELECT count(*) FROM dormitory_beds b JOIN dormitory_rooms r ON r.id=b.room_id JOIN dormitory_buildings g ON g.id=r.building_id WHERE b.status='ACTIVE' AND r.status='ACTIVE' AND g.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM dormitory_assignments a WHERE a.bed_id=b.id AND a.status='ASSIGNED')) AS available_beds";
    private final NamedParameterJdbcTemplate jdbc;
    OwnerDashboardAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Owner owner() { return Owner.DORMITORY; }

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
