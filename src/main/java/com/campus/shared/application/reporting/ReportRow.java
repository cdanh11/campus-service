package com.campus.shared.application.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Explicit safe projections; columns and cells have a fixed versioned order. */
public sealed interface ReportRow {
    List<String> cells();
    private static List<String> values(Object... values) {
        return Arrays.stream(values).map(value -> value==null?"":value instanceof BigDecimal amount?amount.toPlainString():value.toString()).toList();
    }
    record Debt(UUID studentId,long chargeCount,BigDecimal principalVnd,BigDecimal paidVnd,BigDecimal outstandingVnd) implements ReportRow {
        public List<String> cells() { return values(studentId,chargeCount,principalVnd,paidVnd,outstandingVnd); }
    }
    record Accommodation(UUID id,UUID studentId,UUID bedId,UUID roomId,UUID buildingId,Instant assignedAt) implements ReportRow {
        public List<String> cells() { return values(id,studentId,bedId,roomId,buildingId,assignedAt); }
    }
    record Enrollment(UUID id,UUID studentId,UUID sectionId,UUID offeringId,UUID termId,UUID courseId,String status,Instant updatedAt) implements ReportRow {
        public List<String> cells() { return values(id,studentId,sectionId,offeringId,termId,courseId,status,updatedAt); }
    }
    record Membership(UUID id,UUID eventId,UUID studentId,String eventCode,String eventTitle,String status,Instant registeredAt,Instant cancelledAt,Instant attendedAt) implements ReportRow {
        public List<String> cells() { return values(id,eventId,studentId,eventCode,eventTitle,status,registeredAt,cancelledAt,attendedAt); }
    }
    record Loan(UUID id,UUID studentId,UUID copyId,UUID titleId,String copyCode,String title,Instant borrowedAt,Instant dueAt,boolean overdue) implements ReportRow {
        public List<String> cells() { return values(id,studentId,copyId,titleId,copyCode,title,borrowedAt,dueAt,overdue); }
    }
}
