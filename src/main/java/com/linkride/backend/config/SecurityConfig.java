package com.linkride.backend.config;

import com.linkride.backend.filter.AuthRateLimitFilter;
import com.linkride.backend.filter.CorrelationIdFilter;
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
 *   <li><b>Public routes</b>: {@code /api/v1/auth/**} (login/signup) and {@code /api/v1/test}
 *       (plain liveness check) are open. Every other route requires a verified JWT.
 *       {@code /api/v1/users/test} and {@code /api/v1/devtools/**} are also matched here, but both
 *       404 unless {@code linkride.devtools.enabled=true} -- the controller beans themselves
 *       aren't registered otherwise, so {@code permitAll()} never actually exposes them outside a
 *       dev-configured environment (Phase 5 §4).</li>
 *   <li><b>Filter order</b>: {@link CorrelationIdFilter} runs first so every request --
 *       including auth failures -- carries a correlation ID, then {@link JwtAuthFilter} runs
 *       before Spring's own {@link UsernamePasswordAuthenticationFilter}, ensuring the
 *       {@code SecurityContextHolder} is populated before any authorization
 *       checks happen.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final AuthRateLimitFilter authRateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

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
                .requestMatchers("/api/v1/auth/**").permitAll()      // Login & Signup: open
                .requestMatchers("/api/v1/users/test").permitAll()  // Legacy test endpoint: open
                .requestMatchers("/api/v1/test").permitAll()        // Liveness check: open (Phase 5 §10 gap fix)
                .requestMatchers("/error").permitAll()              // Allow Spring Boot error handling (prevents 403 on 400 errors)
                // Health/info only -- deployment platforms probe these without a JWT (Phase 5 §8).
                // The broader actuator surface (dev-only, see application-dev.properties) is
                // NOT listed here, so it falls through to anyRequest().authenticated() below.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // OpenAPI spec + Swagger UI (Phase 5 §7). Swagger UI itself 404s in prod
                // (springdoc.swagger-ui.enabled=false there) regardless of this permitAll.
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                // Developer tool: only reachable at all when linkride.devtools.enabled=true
                // registers the controller (see devtools package) -- otherwise this route 404s.
                .requestMatchers("/api/v1/devtools/**").permitAll()
                .anyRequest().authenticated()                       // Everything else: requires JWT
            )

            // 4. Route auth failures/denials from the security chain itself (not from a
            //    controller) onto the same ErrorResponse envelope as everything else (Phase 5 §10).
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler))

            // 5. Register our Supabase JWT filter before the standard username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // 6. Rate limiting (Phase 5 §11) runs before the JWT filter -- it only ever acts on
            //    /api/v1/auth/**, which never carries a JWT anyway, so this ordering costs nothing
            //    on every other route and blocks abuse as early in the chain as possible.
            .addFilterBefore(authRateLimitFilter, JwtAuthFilter.class)

            // 7. Correlation ID must be assigned before the rate limiter runs, so a 429 response
            //    (and its log line) already carries it, same as every other error path.
            .addFilterBefore(correlationIdFilter, AuthRateLimitFilter.class);

        return http.build();
    }
}
