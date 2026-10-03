package com.routeassign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * Current state
 * ─────────────
 * JWT filter is not yet wired in — the full authentication layer will be added
 * in a later phase.  Until then, most endpoints are open.
 *
 * Phase 2 addition: the scoring-rules PUT endpoint is explicitly restricted to
 * ADMIN role at the URL level here, and also at the method level via
 * {@code @PreAuthorize} in {@code AssignmentScoringRuleController} (defence in depth).
 *
 * {@code @EnableMethodSecurity} activates {@code @PreAuthorize} / {@code @PostAuthorize}
 * on any Spring-managed bean, not just controllers.
 *
 * TODO (future JWT phase):
 *   .requestMatchers(POST, "/api/auth/**").permitAll()
 *   .requestMatchers(GET,  "/api/items/**").permitAll()
 *   .requestMatchers("/api/orders/**").hasRole("CUSTOMER")
 *   .requestMatchers("/api/assignments/**").hasAnyRole("DELIVERY_PARTNER", "ADMIN")
 *   .anyRequest().authenticated()
 *   .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // activates @PreAuthorize on beans
public class SecurityConfig {

    /**
     * Password encoder bean — BCrypt with default strength (10 rounds).
     * Injected into AuthServiceImpl for hashing and verification.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security filter chain.
     *
     * Explicit rule: PUT /api/v1/scoring-rules/** → ADMIN only.
     * All other requests remain open until the JWT filter is introduced.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // ── Phase 2: Scoring rule writes are ADMIN only ───────────
                    .requestMatchers(HttpMethod.PUT, "/api/v1/scoring-rules/**")
                            .hasRole(AppConstants.ROLE_ADMIN)
                    // ── Phase 1: Assignment rule writes are ADMIN only ────────
                    .requestMatchers(HttpMethod.PUT, "/api/v1/assignment-rules/**")
                            .hasRole(AppConstants.ROLE_ADMIN)
                    // ── Everything else: open until JWT filter is wired ───────
                    .anyRequest().permitAll()
            );
        return http.build();
    }
}
