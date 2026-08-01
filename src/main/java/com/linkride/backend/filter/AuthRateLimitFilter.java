package com.linkride.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP, in-memory rate limiting for {@code /api/v1/auth/**} only (Phase 5 §11 — see
 * backend/docs/phase-5-platform-hardening.md). This is the only unauthenticated, public,
 * write-capable route surface in the app -- every other route already caps abuse to the cost
 * of obtaining a JWT, so this is deliberately not applied globally.
 *
 * <p>In-memory token buckets, keyed by {@link HttpServletRequest#getRemoteAddr()}, do not survive
 * a restart or synchronize across multiple instances -- an accepted limitation at LinkRide's
 * current single-instance scale, not solved prematurely with a Redis dependency (see the
 * architecture doc's Future Extensions for §11).</p>
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/auth/";

    private final ObjectMapper objectMapper;
    private final int capacity;
    private final int refillTokens;
    private final int refillPeriodSeconds;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${linkride.ratelimit.auth.capacity:5}") int capacity,
            @Value("${linkride.ratelimit.auth.refill-tokens:5}") int refillTokens,
            @Value("${linkride.ratelimit.auth.refill-period-seconds:60}") int refillPeriodSeconds) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodSeconds = refillPeriodSeconds;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded on [{}] from [{}]", request.getRequestURI(), clientIp);
        response.setHeader("Retry-After", String.valueOf(refillPeriodSeconds));
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED", "Too many requests. Please try again later.", request);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillPeriodSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }
}
