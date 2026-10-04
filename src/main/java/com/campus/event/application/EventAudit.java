package com.campus.event.application;

import java.time.Instant;
import java.util.UUID;

public interface EventAudit {
    enum Resource { EVENT, REGISTRATION }
    void record(UUID actor,Resource resource,UUID target,String action,long version,String status,Instant time);
    default void record(UUID actor,UUID target,String action,long version,String status,Instant time) {
        record(actor,Resource.EVENT,target,action,version,status,time);
    }
    final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) { super(cause); }
    }
}
