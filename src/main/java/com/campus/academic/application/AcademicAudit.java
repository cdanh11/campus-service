package com.campus.academic.application;

import java.time.Instant;
import java.util.UUID;

public interface AcademicAudit {
    void record(UUID actorId, Resource resource, UUID targetId, Action action, long version, String status, Instant time);
    enum Resource { PROGRAM, COURSE, TERM, COURSE_OFFERING, CLASS_SECTION, ENROLLMENT }
    enum Action { CREATED, UPDATED, WITHDRAWN, REENROLLED }
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super("Academic audit could not be recorded", cause); }
    }
}
