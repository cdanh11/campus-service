package com.campus.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.AdminAuditEventRepository;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.RoleRepository;
import com.campus.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserManagementServiceTest {

    @Test
    void preservesUnexpectedExceptionsDuringCreation() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(roles.findByCode(RoleCode.USER)).thenReturn(Optional.of(new Role(UUID.randomUUID(), RoleCode.USER)));
        when(passwords.encode("valid-password")).thenReturn("{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOeGXeZ3dFi2hCojL1DMMb4j3Z4m8U50W");
        Clock failingClock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { throw new IllegalArgumentException("unexpected clock failure"); }
        };
        AdminUserManagementService service = new AdminUserManagementService(users, roles, passwords, mock(SecurityMutationCoordinator.class), mock(AdminAuditEventRepository.class), failingClock);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), "user@campus.example", "Valid User", "valid-password", java.util.List.of("USER"), AccountStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unexpected clock failure")
                .isNotInstanceOf(AdminUserManagementService.RequestValidationException.class);
    }
}
