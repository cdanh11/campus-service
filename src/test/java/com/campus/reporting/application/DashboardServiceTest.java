package com.campus.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.campus.shared.application.reporting.DashboardContributor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DashboardServiceTest {
    @Test void rejectsMissingOrDuplicateOwnerContracts() {
        assertThatThrownBy(() -> new DashboardService(List.of())).isInstanceOf(IllegalStateException.class);
        var owner = port(DashboardContributor.Owner.IDENTITY, Map.of("users", BigDecimal.ZERO));
        assertThatThrownBy(() -> new DashboardService(List.of(owner, owner))).isInstanceOf(IllegalStateException.class);
    }
    @Test void usesOneInstantAndRejectsNegativeOrDuplicateMetrics() {
        var seen = new ArrayList<Instant>();
        var ports = Arrays.stream(DashboardContributor.Owner.values()).<DashboardContributor>map(owner -> new DashboardContributor() {
            public Owner owner() { return owner; }
            public Map<String, BigDecimal> metrics(Instant at) { seen.add(at); return Map.of(owner.name(), BigDecimal.ZERO); }
        }).toList();
        var dashboard = new DashboardService(ports).dashboard();
        assertThat(dashboard.groups()).hasSize(8);
        assertThat(seen).hasSize(10).allMatch(dashboard.asOf()::equals);
        var broken = new ArrayList<DashboardContributor>(ports);
        broken.set(0, port(DashboardContributor.Owner.IDENTITY, Map.of("users", BigDecimal.valueOf(-1))));
        assertThatThrownBy(() -> new DashboardService(broken).dashboard()).isInstanceOf(IllegalStateException.class);
        broken.set(0, ports.get(0));
        broken.set(1, port(DashboardContributor.Owner.ORGANIZATION, Map.of("duplicate", BigDecimal.ZERO)));
        broken.set(2, port(DashboardContributor.Owner.STUDENT, Map.of("duplicate", BigDecimal.ZERO)));
        assertThatThrownBy(() -> new DashboardService(broken).dashboard()).isInstanceOf(IllegalStateException.class);
    }
    private DashboardContributor port(DashboardContributor.Owner owner, Map<String, BigDecimal> values) {
        return new DashboardContributor() {
            public Owner owner() { return owner; }
            public Map<String, BigDecimal> metrics(Instant at) { return values; }
        };
    }
}
