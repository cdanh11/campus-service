package com.campus.identity.api;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import com.campus.identity.application.AuthenticationService;
import com.campus.identity.domain.Role;
import com.campus.identity.domain.UserAccount;
import com.campus.identity.infrastructure.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationService service;
    private final SecurityProperties properties;
    public AuthController(AuthenticationService service, SecurityProperties properties) { this.service = service; this.properties = properties; }
    @PostMapping("/login") public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.AuthResult result = service.login(request.email(), request.password());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), false).toString()).body(new LoginResponse(result.accessToken(), "Bearer", 900, user(result.user())));
    }
    @PostMapping("/refresh") public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request) {
        String raw = cookieValue(request);
        if (raw == null) throw new AuthenticationService.RefreshTokenMissing();
        AuthenticationService.AuthResult result = service.refresh(raw);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), false).toString()).body(new RefreshResponse(result.accessToken(), "Bearer", 900));
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request) {
        service.logout(cookieValue(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", true).toString()).build();
    }
    @GetMapping("/me") public UserResponse me(Authentication authentication) { return user(service.currentUser((UUID) authentication.getPrincipal())); }
    private String cookieValue(HttpServletRequest request) { return request.getCookies() == null ? null : Arrays.stream(request.getCookies()).filter(c -> c.getName().equals(properties.refreshCookie().name())).map(Cookie::getValue).findFirst().orElse(null); }
    private ResponseCookie cookie(String value, boolean clear) { return ResponseCookie.from(properties.refreshCookie().name(), value).httpOnly(true).secure(properties.refreshCookie().secure()).sameSite(properties.refreshCookie().sameSite()).path(properties.refreshCookie().path()).maxAge(clear ? Duration.ZERO : properties.refreshTokenTtl()).build(); }
    private static UserResponse user(UserAccount user) { return new UserResponse(user.id(), user.email(), user.status().name(), user.roles().stream().map(Role::code).map(Enum::name).collect(java.util.stream.Collectors.toSet())); }
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) { }
    public record RefreshResponse(String accessToken, String tokenType, long expiresIn) { }
    public record UserResponse(UUID id, String email, String status, Set<String> roles) { }
}
