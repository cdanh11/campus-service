package com.campus.dormitory.application;

import java.util.Map;
import java.util.Set;
import com.campus.shared.application.audit.*;

/** Read contract owned by DORMITORY; metadata only exposes statuses this owner records. */
public interface DormitoryAuditQueries extends AuditReadPort {
    default AuditSource source() { return AuditSource.DORMITORY; }
    default AuditPolicy policy() { return new AuditPolicy(16, Map.of("BUILDING", Set.of("ACTIVE","INACTIVE"), "ROOM", Set.of("ACTIVE","INACTIVE"), "BED", Set.of("ACTIVE","INACTIVE"), "ASSIGNMENT", Set.of("ASSIGNED","RELEASED"))); }
}
