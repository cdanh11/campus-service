package com.campus.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AdminAuditAction;
import com.campus.identity.domain.AdminAuditEvent;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountPage;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.domain.UserAccountSearch;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserManagementService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final SecurityMutationCoordinator mutations;
    private final AdminAuditEventRepository audits;
    private final Clock clock;

    public AdminUserManagementService(UserAccountRepository users, RoleRepository roles, PasswordEncoder passwords, SecurityMutationCoordinator mutations, AdminAuditEventRepository audits, Clock clock) {
        this.users = users; this.roles = roles; this.passwords = passwords; this.mutations = mutations; this.audits = audits; this.clock = clock;
    }

    @Transactional
    public UserAccount create(UUID actorId, String email, String displayName, String initialPassword, List<String> roleNames, AccountStatus status) {
        String password = validPassword(initialPassword);
        Set<Role> assigned = roles(roleNames);
        UserAccount user;
        try {
            user = UserAccount.create(UUID.randomUUID(), email, displayName, passwords.encode(password), status == null ? AccountStatus.ACTIVE : status, assigned, clock.instant());
        } catch (UserAccount.InvalidUserAccountException exception) {
            throw new RequestValidationException();
        }
        try {
            UserAccount saved = users.save(user);
            audits.save(new AdminAuditEvent(UUID.randomUUID(), actorId, saved.id(), AdminAuditAction.USER_CREATED, clock.instant(), "{}"));
            return saved;
        } catch (DataIntegrityViolationException exception) { throw new EmailAlreadyExistsException(); }
    }

    public UserAccount get(UUID id) { return users.findById(id).orElseThrow(UserNotFoundException::new); }
    public UserAccountPage search(UserAccountSearch search) { return users.search(search); }
    public UserAccount changeStatus(UUID actorId, UUID targetId, AccountStatus status, long version) { self(actorId, targetId); try { return mutations.changeStatus(actorId, targetId, status, version); } catch (java.util.NoSuchElementException e) { throw new UserNotFoundException(); } }
    public UserAccount replaceRoles(UUID actorId, UUID targetId, List<String> roleNames, long version) { self(actorId, targetId); try { return mutations.replaceRoles(actorId, targetId, roles(roleNames), version); } catch (java.util.NoSuchElementException e) { throw new UserNotFoundException(); } }
    public void resetPassword(UUID actorId, UUID targetId, String password, long version) { self(actorId, targetId); try { mutations.resetPassword(actorId, targetId, passwords.encode(validPassword(password)), version); } catch (java.util.NoSuchElementException e) { throw new UserNotFoundException(); } }

    private Set<Role> roles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) throw new RequestValidationException();
        if (roleNames.stream().anyMatch(role -> role == null || role.isBlank())) throw new RequestValidationException();
        Set<String> normalized = roleNames.stream().map(String::trim).map(role -> role.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalized.size() != roleNames.size()) throw new RequestValidationException();
        try { return normalized.stream().map(RoleCode::valueOf).map(code -> roles.findByCode(code).orElseThrow(UnknownRoleException::new)).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
        catch (IllegalArgumentException exception) { throw new UnknownRoleException(); }
    }
    private static String validPassword(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) < 12 || value.codePointCount(0, value.length()) > 64 || value.getBytes(StandardCharsets.UTF_8).length > 72) throw new RequestValidationException();
        return value;
    }
    private static void self(UUID actor, UUID target) { if (actor.equals(target)) throw new SelfModificationNotAllowedException(); }

    public static final class UserNotFoundException extends RuntimeException { }
    public static final class EmailAlreadyExistsException extends RuntimeException { }
    public static final class UnknownRoleException extends RuntimeException { }
    public static final class RequestValidationException extends RuntimeException { }
    public static final class SelfModificationNotAllowedException extends RuntimeException { }
}
