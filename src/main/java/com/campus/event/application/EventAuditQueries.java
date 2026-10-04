package com.campus.event.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by EVENT; metadata only exposes statuses this owner records. */
public interface EventAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.EVENT; }
    default AuditPolicy policy() { return new AuditPolicy(16, Map.of("EVENT", Set.of("DRAFT","OPEN","CLOSED","CANCELLED"), "REGISTRATION", Set.of("REGISTERED","CANCELLED","ATTENDED"))); }
}
