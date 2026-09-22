package com.campus.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    UserAccount save(UserAccount userAccount);

    UserAccount saveAdminMutation(UserAccount userAccount, long expectedVersion);

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByIdForUpdate(UUID id);

    Optional<UserAccount> findByEmail(String email);

    long countActiveAdministrators();

    UserAccountPage search(UserAccountSearch search);
}
