package com.campus.library.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by LIBRARY; metadata only exposes statuses this owner records. */
public interface LibraryAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.LIBRARY; }
    default AuditPolicy policy() { return new AuditPolicy(16, Map.of("TITLE", Set.of("ACTIVE","INACTIVE"), "COPY", Set.of("ACTIVE","INACTIVE"), "LOAN", Set.of("OPEN","RETURNED"))); }
}
