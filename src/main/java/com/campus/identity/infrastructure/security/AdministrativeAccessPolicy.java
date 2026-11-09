package com.campus.identity.infrastructure.security;

import java.util.List;
import java.util.stream.Stream;

/** Explicit path/method grants for department roles. All unmatched administrator paths stay global-ADMIN-only. */
final class AdministrativeAccessPolicy {
    private static final String BASE = "/api/v1/admin/";
    private static final String UUID_REFERENCE = "/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}";
    private AdministrativeAccessPolicy() { }

    record Rule(List<String> paths, List<String> readers, List<String> writers) { }

    static List<Rule> rules() {
        return List.of(
                // Exact reference paths precede the owner wildcards. Auditors cannot read registrations, loans or inboxes.
                rule("ACADEMIC_AFFAIRS", List.of("AUDITOR"), "academic/sections", "academic/sections" + UUID_REFERENCE, "academic/sections/lookup"),
                rule("CAMPUS_SERVICES", List.of("AUDITOR"), "dormitory/beds", "dormitory/beds" + UUID_REFERENCE, "dormitory/lookup/beds"),
                rule("CAMPUS_SERVICES", List.of("AUDITOR"), "library/copies", "library/copies" + UUID_REFERENCE, "library/copies/lookup"),
                rule("CAMPUS_SERVICES", List.of("AUDITOR"), "events", "events" + UUID_REFERENCE, "events/lookup"),
                rule("ADMIN", List.of("ACADEMIC_AFFAIRS", "CAMPUS_SERVICES"), "users", "users" + UUID_REFERENCE, "users/lookup"),
                rule("ACADEMIC_AFFAIRS", List.of(), "organization-units", "organization-units/**"),
                rule("ACADEMIC_AFFAIRS", List.of("CAMPUS_SERVICES", "FINANCE", "AUDITOR"), "students", "students/**"),
                rule("ACADEMIC_AFFAIRS", List.of(), "faculty-staff", "faculty-staff/**"),
                rule("ACADEMIC_AFFAIRS", List.of(), "academic/**"),
                rule("CAMPUS_SERVICES", List.of(), "dormitory/**"),
                rule("FINANCE", List.of(), "finance/**"),
                rule("CAMPUS_SERVICES", List.of(), "notifications/**"),
                rule("CAMPUS_SERVICES", List.of(), "events/**", "event-registrations", "event-registrations/**"),
                rule("CAMPUS_SERVICES", List.of(), "library/**"),
                rule("ADMIN", List.of("AUDITOR"), "audits/**"),
                rule("ADMIN", List.of("AUDITOR"), "reports/**")
        );
    }

    private static Rule rule(String operator, List<String> referenceReaders, String... paths) {
        List<String> writers = Stream.of("ADMIN", operator).distinct().toList();
        List<String> readers = Stream.concat(writers.stream(), referenceReaders.stream()).distinct().toList();
        return new Rule(Stream.of(paths).map(path -> BASE + path).toList(), readers, writers);
    }
}
