package com.campus.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AuthSession;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.domain.RefreshToken;
import com.campus.identity.domain.RefreshTokenRepository;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountRepository;
import com.campus.identity.infrastructure.security.SecurityProperties;
import com.campus.identity.infrastructure.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private static final String DUMMY_PASSWORD_HASH = "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde";
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final SecurityProperties properties;
    private final Clock clock;

    public AuthenticationService(UserAccountRepository users, AuthSessionRepository sessions, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder, TokenService tokens, SecurityProperties properties, Clock clock) {
        this.users = users; this.sessions = sessions; this.refreshTokens = refreshTokens; this.passwordEncoder = passwordEncoder;
        this.tokens = tokens; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public AuthResult login(String email, String password) {
        UserAccount user = users.findByEmail(email).orElse(null);
        String storedHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (user == null || !isActive(user) || !passwordMatches) throw new AuthenticationFailure();
        Instant now = clock.instant();
        user.recordLogin(now);
        users.save(user);
        AuthSession session = sessions.save(new AuthSession(UUID.randomUUID(), user.id(), now, now.plus(properties.refreshTokenTtl()), null, null));
        String raw = tokens.encodeRefreshToken(tokens.newRefreshToken());
        refreshTokens.save(new RefreshToken(UUID.randomUUID(), session.id(), tokens.hash(raw), now,
                now.plus(properties.refreshTokenTtl()), null, null));
        return new AuthResult(user, tokens.accessToken(user), raw);
    }

    @Transactional(noRollbackFor = RefreshTokenReused.class)
    public AuthResult refresh(String raw) {
        Instant now = clock.instant();
        RefreshToken old = refreshTokens.findByTokenHashForUpdate(tokens.hash(raw)).orElseThrow(RefreshTokenInvalid::new);
        AuthSession session = sessions.findById(old.sessionId()).orElseThrow(RefreshTokenInvalid::new);
        if (old.revokedAt() != null) {
            if (old.replacedById() != null && session.revokedAt() == null) { session.revoke(now, "REFRESH_TOKEN_REUSE"); sessions.save(session); }
            if (old.replacedById() != null) throw new RefreshTokenReused();
            throw new RefreshTokenRevoked();
        }
        if (!old.expiresAt().isAfter(now) || !session.expiresAt().isAfter(now)) throw new RefreshTokenExpired();
        if (session.revokedAt() != null) throw new RefreshTokenRevoked();
        UserAccount user = users.findById(session.userId()).filter(this::isActive).orElseThrow(RefreshTokenRevoked::new);
        String replacement = tokens.encodeRefreshToken(tokens.newRefreshToken());
        RefreshToken next = refreshTokens.save(new RefreshToken(UUID.randomUUID(), session.id(), tokens.hash(replacement), now,
                now.plus(properties.refreshTokenTtl()), null, null));
        old.revoke(now, next.id());
        refreshTokens.save(old);
        return new AuthResult(user, tokens.accessToken(user), replacement);
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshTokens.findByTokenHashForUpdate(tokens.hash(raw)).filter(token -> token.revokedAt() == null)
                .ifPresent(token -> { token.revoke(clock.instant(), null); refreshTokens.save(token); });
    }

    @Transactional(readOnly = true)
    public UserAccount currentUser(UUID id) {
        UserAccount user = users.findById(id).orElseThrow(AuthenticationFailure::new);
        if (!isActive(user)) throw new AccountNotActive();
        return user;
    }

    private boolean isActive(UserAccount user) { return user.status() == AccountStatus.ACTIVE; }
    public record AuthResult(UserAccount user, String accessToken, String refreshToken) { }
    public static class AuthenticationFailure extends RuntimeException { }
    public static class RefreshFailure extends RuntimeException { }
    public static class RefreshTokenMissing extends RefreshFailure { }
    public static class RefreshTokenInvalid extends RefreshFailure { }
    public static class RefreshTokenExpired extends RefreshFailure { }
    public static class RefreshTokenRevoked extends RefreshFailure { }
    public static class RefreshTokenReused extends RefreshFailure { }
    public static class AccountNotActive extends RuntimeException { }
}
