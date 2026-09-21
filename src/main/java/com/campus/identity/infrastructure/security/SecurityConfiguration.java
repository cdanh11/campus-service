package com.campus.identity.infrastructure.security;

import java.time.Clock;

import com.campus.identity.api.SecurityErrorResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean TokenService tokenService(SecurityProperties properties, Clock clock) { return new TokenService(properties, clock); }
    @Bean SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) { return new SecurityErrorResponseWriter(objectMapper); }
    @Bean JsonAccessDeniedHandler jsonAccessDeniedHandler(SecurityErrorResponseWriter errors) { return new JsonAccessDeniedHandler(errors); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokenService, SecurityErrorResponseWriter errors, JsonAccessDeniedHandler denied) throws Exception {
        // Bearer access tokens are stateless; refresh endpoints validate their cookie in the controller.
        return http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint((request, response, exception) -> errors.write(request, response, 401, "MISSING_ACCESS_TOKEN", "Access token is required"))
                        .accessDeniedHandler(denied))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll().anyRequest().authenticated())
                .addFilterBefore(new OriginProtectionFilter(tokenService.properties(), errors), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(tokenService, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
