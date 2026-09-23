package com.campus.identity.domain;

import java.util.Objects;
import java.util.UUID;

public record Role(UUID id, RoleCode code) {

    public Role {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
    }
}
