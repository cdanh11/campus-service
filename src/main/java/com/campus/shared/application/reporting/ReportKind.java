package com.campus.shared.application.reporting;

import java.util.List;
import java.util.Set;

public enum ReportKind {
    STUDENT_DEBT(List.of("studentId","chargeCount","principalVnd","paidVnd","outstandingVnd"), Set.of(), false),
    CURRENT_ACCOMMODATION(List.of("id","studentId","bedId","roomId","buildingId","assignedAt"), Set.of(), true),
    SECTION_ENROLLMENT(List.of("id","studentId","sectionId","offeringId","termId","courseId","status","updatedAt"), Set.of("ENROLLED","WITHDRAWN"), true),
    EVENT_MEMBERSHIP(List.of("id","eventId","studentId","eventCode","eventTitle","status","registeredAt","cancelledAt","attendedAt"), Set.of("REGISTERED","CANCELLED","ATTENDED"), true),
    LIBRARY_LOANS(List.of("id","studentId","copyId","titleId","copyCode","title","borrowedAt","dueAt","overdue"), Set.of(), true);

    private final List<String> columns;
    private final Set<String> statuses;
    private final boolean resourceFilter;
    ReportKind(List<String> columns, Set<String> statuses, boolean resourceFilter) {
        this.columns=columns; this.statuses=statuses; this.resourceFilter=resourceFilter;
    }
    public List<String> columns() { return columns; }
    public void validate(ReportSearch search) {
        if (search.status()!=null && !statuses.contains(search.status())
                || search.resourceId()!=null && !resourceFilter
                || search.overdueOnly() && this!=LIBRARY_LOANS)
            throw new IllegalArgumentException("Unsupported report filter");
    }
}
