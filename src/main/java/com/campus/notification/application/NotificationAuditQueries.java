package com.campus.notification.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by NOTIFICATION; metadata only exposes statuses this owner records. */
public interface NotificationAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.NOTIFICATION; }
    default AuditPolicy policy() { return new AuditPolicy(16, Map.of("TEMPLATE", Set.of("ACTIVE","INACTIVE"), "NOTICE", Set.of("DRAFT","PUBLISHED"), "DELIVERY", Set.of("UNREAD","READ"))); }
}
