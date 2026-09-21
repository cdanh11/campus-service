package com.campus.identity.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserAccountTest {

    private static final String PASSWORD_HASH = "{bcrypt}$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZabcde";

    @Test
    void createsActiveAccountWithNormalizedEmail() {
        UserAccount account = UserAccount.create(UUID.randomUUID(), " Admin@Campus.Example ", PASSWORD_HASH, Instant.now());

        assertThat(account.email()).isEqualTo("admin@campus.example");
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.roles()).isEmpty();
    }

    @Test
    void acceptsValidApprovedBcryptEncodedValue() {
        UserAccount account = UserAccount.create(UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH, Instant.now());

        assertThat(account.passwordHash()).isEqualTo(PASSWORD_HASH);
    }

    @Test
    void rejectsRawPlaintextPassword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(UUID.randomUUID(), "admin@campus.example", "password", Instant.now()))
                .withMessageContaining("passwordHash");
    }

    @Test
    void rejectsBcryptPrefixFollowedByPlaintext() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", "{bcrypt}plaintext-password", Instant.now()));
    }

    @Test
    void rejectsTruncatedBcryptValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH.substring(0, PASSWORD_HASH.length() - 1), Instant.now()));
    }

    @Test
    void rejectsUnsupportedBcryptVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH.replace("$2b$", "$2x$"), Instant.now()));
    }

    @Test
    void rejectsBcryptCostOutsideApprovedRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH.replace("$12$", "$09$"), Instant.now()));
    }

    @Test
    void rejectsBcryptValueWithInvalidCharacters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH.substring(0, PASSWORD_HASH.length() - 1) + "?", Instant.now()));
    }

    @Test
    void rejectsBcryptValueWithWhitespaceMutation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserAccount.create(
                        UUID.randomUUID(), "admin@campus.example", " " + PASSWORD_HASH, Instant.now()));
    }

    @Test
    void rejectsDuplicateRoleCodesAndAllowsAnyStatusTransition() {
        UserAccount account = UserAccount.create(UUID.randomUUID(), "admin@campus.example", PASSWORD_HASH, Instant.now());
        Role user = new Role(UUID.randomUUID(), RoleCode.USER);
        Role duplicateUser = new Role(UUID.randomUUID(), RoleCode.USER);

        assertThatIllegalArgumentException().isThrownBy(() -> account.replaceRoles(Set.of(user, duplicateUser)));
        account.changeStatus(AccountStatus.SUSPENDED);
        account.changeStatus(AccountStatus.DISABLED);
        account.changeStatus(AccountStatus.ACTIVE);

        assertThat(account.roles()).isEmpty();
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    }
}
