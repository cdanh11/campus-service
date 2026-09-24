package com.campus.identity.infrastructure.persistence;

import java.util.UUID;

import com.campus.identity.application.IdentityUserDirectory;
import org.springframework.stereotype.Component;

@Component
class IdentityUserDirectoryAdapter implements IdentityUserDirectory {
    private final UserAccountJpaRepository users;
    IdentityUserDirectoryAdapter(UserAccountJpaRepository users) { this.users = users; }
    @Override public boolean exists(UUID userId) { return users.existsById(userId); }
}
