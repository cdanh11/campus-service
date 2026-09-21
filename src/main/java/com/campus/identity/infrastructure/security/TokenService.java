package com.campus.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.campus.identity.domain.Role;
import com.campus.identity.domain.UserAccount;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

public final class TokenService {
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] key = Base64.getDecoder().decode(properties.jwt().secret());
        if (key.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 256 bits");
        }
        SecretKey secretKey = new SecretKeySpec(key, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
        NimbusJwtDecoder configuredDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> issuer = jwt -> properties.jwt().issuer().equals(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_issuer"));
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audience = jwt -> jwt.getAudience().contains(properties.jwt().audience())
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
        configuredDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience));
        this.decoder = configuredDecoder;
    }

    public String accessToken(UserAccount user) {
        Instant now = clock.instant();
        List<String> roles = user.roles().stream().map(Role::code).map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.jwt().issuer()).audience(List.of(properties.jwt().audience()))
                .subject(user.id().toString()).issuedAt(now).expiresAt(now.plus(properties.jwt().accessTokenTtl()))
                .id(UUID.randomUUID().toString()).claim("roles", roles).build();
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public JwtDecoder decoder() { return decoder; }
    public SecurityProperties properties() { return properties; }
    public Instant now() { return clock.instant(); }
    public byte[] newRefreshToken() { byte[] value = new byte[32]; random.nextBytes(value); return value; }
    public String encodeRefreshToken(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    public byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
