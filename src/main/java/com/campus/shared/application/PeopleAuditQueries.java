package com.campus.shared.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by PEOPLE; metadata only exposes statuses this owner records. */
public interface PeopleAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.PEOPLE; }
    default AuditPolicy policy() { return new AuditPolicy(32, Map.of("ORGANIZATION_UNIT", Set.of(), "STUDENT", Set.of(), "FACULTY_STAFF", Set.of())); }
}
