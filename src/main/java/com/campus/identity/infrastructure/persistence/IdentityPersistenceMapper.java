package com.campus.identity.infrastructure.persistence;

import java.util.Set;

import com.campus.identity.domain.AuthSession;
import com.campus.identity.domain.RefreshToken;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.infrastructure.persistence.entity.AuthSessionEntity;
import com.campus.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.campus.identity.infrastructure.persistence.entity.RoleEntity;
import com.campus.identity.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.stereotype.Component;

@Component
class IdentityPersistenceMapper {

    UserAccount toDomain(UserAccountEntity entity) {
        Set<Role> roles = entity.getRoles().stream().map(this::toDomain).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return UserAccount.rehydrate(
                entity.getId(),
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getPasswordHash(),
                entity.getStatus(),
                roles,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastLoginAt(),
                entity.getSecurityVersion(), entity.getRowVersion());
    }

    Role toDomain(RoleEntity entity) {
        return new Role(entity.getId(), entity.getCode());
    }

    AuthSession toDomain(AuthSessionEntity entity) {
        return new AuthSession(
                entity.getId(),
                entity.getUser().getId(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getRevocationReason());
    }

    RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                entity.getId(),
                entity.getSession().getId(),
                entity.getTokenHash(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedById());
    }
}
