package com.campus.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AdminAuditAction;
import com.campus.identity.domain.AdminAuditEvent;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.domain.AdminGuardRepository;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityMutationCoordinator {

    private final AdminGuardRepository guardRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final Clock clock;

    public SecurityMutationCoordinator(AdminGuardRepository guardRepository, UserAccountRepository userAccountRepository,
            AuthSessionRepository authSessionRepository, AdminAuditEventRepository auditEventRepository, Clock clock) {
        this.guardRepository = guardRepository;
        this.userAccountRepository = userAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Transactional
    public UserAccount changeStatus(UUID actorId, UUID targetId, AccountStatus status, long expectedVersion) {
        return mutate(actorId, targetId, expectedVersion, status != AccountStatus.ACTIVE, AdminAuditAction.STATUS_CHANGED, target -> target.changeStatus(status),
                target -> target.status() == AccountStatus.ACTIVE && status != AccountStatus.ACTIVE, target -> target.status() == status, "STATUS_CHANGED");
    }

    @Transactional
    public UserAccount replaceRoles(UUID actorId, UUID targetId, Set<Role> roles, long expectedVersion) {
        return mutate(actorId, targetId, expectedVersion, !hasAdmin(roles), AdminAuditAction.ROLES_REPLACED, target -> target.replaceRoles(roles),
                target -> hasAdmin(target) && !hasAdmin(roles), target -> false, "ROLES_REPLACED");
    }

    @Transactional
    public UserAccount resetPassword(UUID actorId, UUID targetId, String passwordHash, long expectedVersion) {
        return mutate(actorId, targetId, expectedVersion, false, AdminAuditAction.PASSWORD_RESET, target -> target.replacePasswordHash(passwordHash),
                target -> false, target -> false, "PASSWORD_RESET");
    }

    private UserAccount mutate(UUID actorId, UUID targetId, long expectedVersion, boolean mayReduceAdministrators, AdminAuditAction action,
            java.util.function.Consumer<UserAccount> mutation, java.util.function.Predicate<UserAccount> reducesAdmins,
            java.util.function.Predicate<UserAccount> noOp, String reason) {
        if (expectedVersion < 0) {
            throw new InvalidExpectedVersionException();
        }
        if (mayReduceAdministrators) {
            guardRepository.lock();
        }
        UserAccount target = userAccountRepository.findByIdForUpdate(targetId).orElseThrow();
        if (target.rowVersion() != expectedVersion) {
            throw new ConcurrentModificationException();
        }
        if (noOp.test(target)) {
            return target;
        }
        if (reducesAdmins.test(target) && userAccountRepository.countActiveAdministrators() <= 1) {
            throw new LastActiveAdministratorRequiredException();
        }
        mutation.accept(target);
        Instant now = clock.instant();
        authSessionRepository.revokeActiveSessionsForUser(targetId, now, reason);
        UserAccount saved;
        try {
            saved = userAccountRepository.saveAdminMutation(target, expectedVersion);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConcurrentModificationException();
        }
        auditEventRepository.save(new AdminAuditEvent(UUID.randomUUID(), actorId, targetId, action, now, "{}"));
        return saved;
    }

    private static boolean hasAdmin(UserAccount account) { return hasAdmin(account.roles()); }
    private static boolean hasAdmin(Set<Role> roles) { return roles.stream().anyMatch(role -> role.code() == RoleCode.ADMIN); }

    public static final class InvalidExpectedVersionException extends RuntimeException { }
}
