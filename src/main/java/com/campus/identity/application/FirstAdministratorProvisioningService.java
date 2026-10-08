package com.campus.identity.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Explicit offline setup command; never exposed by a controller or an automatic startup hook. */
@Service
@Validated
public class FirstAdministratorProvisioningService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final AdminGuardRepository guard;
    private final AdminAuditEventRepository audits;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public FirstAdministratorProvisioningService(UserAccountRepository users, RoleRepository roles,
            AdminGuardRepository guard, AdminAuditEventRepository audits, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.guard = guard;
        this.audits = audits;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public UUID provision(@NotBlank @Email String email, @NotBlank String displayName, String password) {
        String validated = AdminUserManagementService.validPassword(password);
        guard.lock();
        // Include inactive administrators: this command is setup, not an account-recovery bypass.
        if (users.search(new UserAccountSearch(0, 1, null, null, RoleCode.ADMIN, "createdAt", true))
                .totalElements() != 0) {
            throw new AdministratorAlreadyExistsException();
        }
        Role role = roles.findByCode(RoleCode.ADMIN).orElseThrow();
        var user = UserAccount.create(UUID.randomUUID(), email, displayName, passwords.encode(validated),
                AccountStatus.ACTIVE, Set.of(role), clock.instant());
        var saved = users.save(user);
        // The first administrator is both the accountable bootstrap actor and target.
        audits.save(new AdminAuditEvent(UUID.randomUUID(), saved.id(), saved.id(), AdminAuditAction.USER_CREATED,
                clock.instant(), "{}"));
        return saved.id();
    }

    public static final class AdministratorAlreadyExistsException extends RuntimeException {
        public AdministratorAlreadyExistsException() { super("An administrator already exists; use normal administration or the approved recovery process."); }
    }
}
