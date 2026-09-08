package com.routeassign.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Spring Security configuration.
 *
 * Currently set to PERMIT_ALL so the project compiles and runs
 * during the scaffolding phase. JWT filter and role-based access
 * control should be added when the authentication layer is implemented.
 */
@Configuration
@EnableWebSecurity
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
     * TODO: replace permitAll() with proper role-based rules and add the
     *       JWT authentication filter once JwtService is implemented:
     *
     *       .requestMatchers(POST, "/api/auth/**").permitAll()
     *       .requestMatchers(GET,  "/api/items/**").permitAll()
     *       .requestMatchers("/api/orders/**").hasRole(ROLE_CUSTOMER)
     *       .requestMatchers("/api/assignments/**").hasAnyRole(ROLE_DELIVERY_PARTNER, ROLE_ADMIN)
     *       .anyRequest().authenticated()
     *       .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()   // TODO: tighten when JWT filter is added
            );
        return http.build();
    }
}
