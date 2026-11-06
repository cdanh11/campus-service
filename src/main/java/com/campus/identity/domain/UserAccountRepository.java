package com.campus.identity.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    UserAccount save(UserAccount userAccount);

    UserAccount saveAdminMutation(UserAccount userAccount, long expectedVersion);

    void recordLogin(UUID id, java.time.Instant at);

    Optional<UserAccount> findById(UUID id);

    List<UserAccount> findAllById(Collection<UUID> ids);

    Optional<UserAccount> findByIdForUpdate(UUID id);

    Optional<UserAccount> findByEmail(String email);

    long countActiveAdministrators();

    UserAccountPage search(UserAccountSearch search);
}
