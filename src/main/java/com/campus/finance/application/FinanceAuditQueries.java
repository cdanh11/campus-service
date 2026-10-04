package com.campus.finance.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by FINANCE; metadata only exposes statuses this owner records. */
public interface FinanceAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.FINANCE; }
    default AuditPolicy policy() { return new AuditPolicy(16, Map.of("FEE", Set.of("ACTIVE","INACTIVE"), "CHARGE", Set.of("OPEN","CANCELLED"), "PAYMENT", Set.of("RECORDED","REVERSED"))); }
}
