package com.campus.academic.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by ACADEMIC; metadata only exposes statuses this owner records. */
public interface AcademicAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.ACADEMIC; }
    default AuditPolicy policy() { return new AuditPolicy(32, Map.of("PROGRAM", Set.of("ACTIVE","INACTIVE"), "COURSE", Set.of("ACTIVE","INACTIVE"), "TERM", Set.of("PLANNED","ACTIVE","CLOSED","CANCELLED"), "COURSE_OFFERING", Set.of("DRAFT","OPEN","CLOSED","CANCELLED"), "CLASS_SECTION", Set.of("DRAFT","OPEN","CLOSED","CANCELLED"), "ENROLLMENT", Set.of("ENROLLED","WITHDRAWN"))); }
}
