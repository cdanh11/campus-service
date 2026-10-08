package com.campus.identity.infrastructure.security;

import java.util.List;
import java.util.stream.Stream;

/** Explicit path/method grants. All unmatched administrator paths stay global-ADMIN-only. */
final class AdministrativeAccessPolicy {
    private static final String BASE = "/api/v1/admin/";
    private static final String UUID_REFERENCE = "/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";
    private AdministrativeAccessPolicy() { }

    record Rule(List<String> paths, List<String> readers, List<String> writers) { }

    static List<Rule> rules() {
        return List.of(
                // Exact reference paths precede owner wildcards. Report viewers cannot read unrelated owner data.
                rule("ACADEMIC_ADMIN", List.of("REPORTING_VIEWER"), "academic/sections", "academic/sections" + UUID_REFERENCE),
                rule("DORMITORY_ADMIN", List.of("REPORTING_VIEWER"), "dormitory/beds", "dormitory/beds" + UUID_REFERENCE),
                rule("LIBRARY_ADMIN", List.of("REPORTING_VIEWER"), "library/copies", "library/copies" + UUID_REFERENCE),
                rule("EVENT_ADMIN", List.of("REPORTING_VIEWER"), "events", "events" + UUID_REFERENCE),
                rule("ADMIN", List.of("STUDENT_ADMIN", "PERSONNEL_ADMIN", "NOTIFICATION_ADMIN"), "users", "users" + UUID_REFERENCE),
                rule("ORGANIZATION_ADMIN", List.of("STUDENT_ADMIN", "PERSONNEL_ADMIN", "ACADEMIC_ADMIN"), "organization-units", "organization-units/**"),
                rule("STUDENT_ADMIN", List.of("ACADEMIC_ADMIN", "DORMITORY_ADMIN", "FINANCE_ADMIN", "EVENT_ADMIN", "LIBRARY_ADMIN", "REPORTING_VIEWER"), "students", "students/**"),
                rule("PERSONNEL_ADMIN", List.of("ACADEMIC_ADMIN"), "faculty-staff", "faculty-staff/**"),
                rule("ACADEMIC_ADMIN", List.of(), "academic/**"),
                rule("DORMITORY_ADMIN", List.of(), "dormitory/**"),
                rule("FINANCE_ADMIN", List.of(), "finance/**"),
                rule("NOTIFICATION_ADMIN", List.of(), "notifications/**"),
                rule("EVENT_ADMIN", List.of(), "events/**", "event-registrations", "event-registrations/**"),
                rule("LIBRARY_ADMIN", List.of(), "library/**"),
                rule("ADMIN", List.of("AUDIT_VIEWER"), "audits/**"),
                rule("ADMIN", List.of("REPORTING_VIEWER"), "reports/**")
        );
    }

    private static Rule rule(String operator, List<String> referenceReaders, String... paths) {
        List<String> writers = Stream.of("ADMIN", operator).distinct().toList();
        List<String> readers = Stream.concat(writers.stream(), referenceReaders.stream()).distinct().toList();
        return new Rule(Stream.of(paths).map(path -> BASE + path).toList(), readers, writers);
    }
}
