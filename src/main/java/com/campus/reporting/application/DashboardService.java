package com.campus.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import com.campus.shared.application.reporting.DashboardContributor.Owner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final Map<Owner, DashboardContributor> owners;

    public DashboardService(List<DashboardContributor> contributors) {
        var registered = new EnumMap<Owner, DashboardContributor>(Owner.class);
        for (var contributor : contributors) {
            if (registered.put(contributor.owner(), contributor) != null) throw new IllegalStateException("Duplicate dashboard owner");
        }
        if (registered.size() != Owner.values().length) throw new IllegalStateException("Missing dashboard owner");
        owners = Collections.unmodifiableMap(registered);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Dashboard dashboard() {
        Instant asOf = Instant.now();
        var groups = new LinkedHashMap<String, Map<String, BigDecimal>>();
        for (var entry : owners.entrySet()) {
            String group = switch (entry.getKey()) {
                case ORGANIZATION, STUDENT, PERSONNEL -> "PEOPLE";
                default -> entry.getKey().name();
            };
            var metrics = groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>());
            entry.getValue().metrics(asOf).forEach((key, value) -> {
                if (value == null || value.signum() < 0 || metrics.putIfAbsent(key, value) != null)
                    throw new IllegalStateException("Invalid or duplicate dashboard metric");
            });
        }
        var frozen = new LinkedHashMap<String, Map<String, BigDecimal>>();
        groups.forEach((key, value) -> frozen.put(key, Collections.unmodifiableMap(value)));
        return new Dashboard(asOf, "VND", Collections.unmodifiableMap(frozen));
    }

    public record Dashboard(Instant asOf, String currency, Map<String, Map<String, BigDecimal>> groups) { }
}
