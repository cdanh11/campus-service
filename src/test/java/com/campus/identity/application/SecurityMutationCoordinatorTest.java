package com.campus.identity.application;

import java.time.Clock;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.domain.AdminGuardRepository;
import com.campus.identity.domain.AuthSessionRepository;
import com.campus.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SecurityMutationCoordinatorTest {

    @Test
    void rejectsNegativeExpectedVersionsWithATypedException() {
        SecurityMutationCoordinator coordinator = new SecurityMutationCoordinator(mock(AdminGuardRepository.class), mock(UserAccountRepository.class),
                mock(AuthSessionRepository.class), mock(AdminAuditEventRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> coordinator.changeStatus(UUID.randomUUID(), UUID.randomUUID(), AccountStatus.SUSPENDED, -1))
                .isInstanceOf(SecurityMutationCoordinator.InvalidExpectedVersionException.class);
    }
}
