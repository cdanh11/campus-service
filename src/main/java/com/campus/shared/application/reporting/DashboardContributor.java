package com.campus.shared.application.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Owner contract: implementations query only data owned by their module. */
public interface DashboardContributor {
    enum Owner { IDENTITY, ORGANIZATION, STUDENT, PERSONNEL, ACADEMIC, DORMITORY, FINANCE, NOTIFICATION, EVENT, LIBRARY }
    Owner owner();
    Map<String, BigDecimal> metrics(Instant asOf);
}
