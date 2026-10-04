package com.campus.identity.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by IDENTITY; metadata only exposes statuses this owner records. */
public interface IdentityAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.IDENTITY; }
    default AuditPolicy policy() { return new AuditPolicy(64, Map.of("USER", Set.of())); }
}
