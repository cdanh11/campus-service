package com.campus.identity.domain;

import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findByCode(RoleCode code);
}
