package com.linkride.backend.config;

import com.linkride.backend.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration for the LinkRide backend.
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li><b>Stateless</b>: No HTTP sessions are created or used; every request must
 *       carry a valid Supabase JWT in the {@code Authorization} header.</li>
 *   <li><b>CSRF disabled</b>: Safe for a REST/mobile backend — CSRF attacks only
 *       apply to browser-cookie-based sessions, which we don't use.</li>
 *   <li><b>Public routes</b>: {@code /api/auth/**} (login/signup) and the legacy
 *       {@code /api/users/test} endpoint are open. Every other route requires a
 *       verified JWT.</li>
 *   <li><b>Filter order</b>: {@link JwtAuthFilter} runs before Spring's own
 *       {@link UsernamePasswordAuthenticationFilter}, ensuring the
 *       {@code SecurityContextHolder} is populated before any authorization
 *       checks happen.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Disable CSRF — we use JWTs, not browser session cookies
            .csrf(AbstractHttpConfigurer::disable)

            // 2. Strictly stateless — Spring will never create an HttpSession
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 3. Route-level access rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()        // Login & Signup: open
                .requestMatchers("/api/users/test").permitAll()     // Legacy test endpoint: open
                .requestMatchers("/error").permitAll()              // Allow Spring Boot error handling (prevents 403 on 400 errors)
                .anyRequest().authenticated()                       // Everything else: requires JWT
            )

            // 4. Register our Supabase JWT filter before the standard username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
