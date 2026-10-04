package com.campus.library.application;

import java.time.Instant;
import java.util.UUID;
import com.campus.library.domain.LibrarySearch.Resource;

public interface LibraryAudit {
    void record(UUID actor, Resource resource, UUID target, String action, long version, String status, Instant time);
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super("Library audit unavailable", cause); }
    }
}
