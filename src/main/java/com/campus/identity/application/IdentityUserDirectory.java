package com.campus.identity.application;

import java.util.UUID;

/** Application-facing read contract for optional people-registry links. */
public interface IdentityUserDirectory {
    boolean exists(UUID userId);
}
