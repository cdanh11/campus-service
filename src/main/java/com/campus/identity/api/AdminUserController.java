package com.campus.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.application.AdminUserManagementService;
import com.campus.identity.domain.AccountStatus;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.RoleCode;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.domain.UserAccountPage;
import com.campus.identity.domain.UserAccountSearch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserManagementService service;
    public AdminUserController(AdminUserManagementService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(Authentication authentication, @Valid @RequestBody CreateAdminUserRequest request) {
        UserAccount created = service.create(actor(authentication), request.email(), request.displayName(), request.initialPassword(), request.roles(), request.status());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{userId}").buildAndExpand(created.id()).toUri()).body(response(created));
    }
    @GetMapping
    public AdminUserPageResponse search(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status, @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > 100 || (q != null && q.trim().codePointCount(0, q.trim().length()) > 100)) throw new InvalidQueryParameterException();
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !Set.of("email", "displayName", "status", "createdAt", "updatedAt").contains(parts[0]) || !(parts[1].equals("asc") || parts[1].equals("desc"))) throw new InvalidQueryParameterException();
        UserAccountPage result = service.search(new UserAccountSearch(page, size, q == null || q.trim().isEmpty() ? null : q.trim(), queryEnum(status, AccountStatus.class), queryEnum(role, RoleCode.class), parts[0], parts[1].equals("asc")));
        return new AdminUserPageResponse(result.content().stream().map(AdminUserController::response).toList(), page, size, result.totalElements(), (int) Math.ceil((double) result.totalElements() / size));
    }
    @GetMapping("/{userId}") public AdminUserResponse get(@PathVariable UUID userId) { return response(service.get(userId)); }
    @PatchMapping("/{userId}/status") public AdminUserResponse changeStatus(Authentication auth, @PathVariable UUID userId, @Valid @RequestBody StatusRequest request) { return response(service.changeStatus(actor(auth), userId, request.status(), request.expectedVersion())); }
    @PutMapping("/{userId}/roles") public AdminUserResponse replaceRoles(Authentication auth, @PathVariable UUID userId, @Valid @RequestBody RolesRequest request) { return response(service.replaceRoles(actor(auth), userId, request.roles(), request.expectedVersion())); }
    @PostMapping("/{userId}/password-reset") public ResponseEntity<Void> resetPassword(Authentication auth, @PathVariable UUID userId, @Valid @RequestBody PasswordResetRequest request) { service.resetPassword(actor(auth), userId, request.newPassword(), request.expectedVersion()); return ResponseEntity.noContent().build(); }

    private static UUID actor(Authentication authentication) { return (UUID) authentication.getPrincipal(); }
    private static <T extends Enum<T>> T queryEnum(String value, Class<T> type) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException exception) { throw new InvalidQueryParameterException(); }
    }
    private static AdminUserResponse response(UserAccount user) { return new AdminUserResponse(user.id(), user.email(), user.displayName(), user.status().name(), user.roles().stream().map(Role::code).map(Enum::name).sorted().toList(), user.securityVersion(), user.rowVersion(), user.createdAt(), user.updatedAt()); }
    public record CreateAdminUserRequest(@Email @NotBlank String email, @NotBlank String displayName, @NotBlank String initialPassword, @NotEmpty List<String> roles, AccountStatus status) { }
    public record StatusRequest(@NotNull AccountStatus status, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record RolesRequest(@NotEmpty List<String> roles, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record PasswordResetRequest(@NotBlank String newPassword, @NotNull @PositiveOrZero Long expectedVersion) { }
    public record AdminUserResponse(UUID id, String email, String displayName, String status, List<String> roles, long securityVersion, long rowVersion, Instant createdAt, Instant updatedAt) { }
    public record AdminUserPageResponse(List<AdminUserResponse> content, int page, int size, long totalElements, int totalPages) { }
    public static final class InvalidQueryParameterException extends RuntimeException { }
}
