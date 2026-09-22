package com.campus.identity.application;

public final class LastActiveAdministratorRequiredException extends RuntimeException {

    public LastActiveAdministratorRequiredException() {
        super("at least one active administrator is required");
    }
}
