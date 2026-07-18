package com.linkride.backend.devtools;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The devtools seeder/scenario calls real service beans (e.g. {@code VehicleService}) in-process
 * rather than over HTTP, so there's no {@code JwtAuthFilter} run populating the security context
 * the way a real request would. This reproduces exactly what that filter does -- a {@link
 * UsernamePasswordAuthenticationToken} whose principal is the raw user UUID string -- for the
 * duration of one call, then clears it. Not a bypass of any authorization check: it stands in for
 * the HTTP/JWT layer only, the same identity a real request from that user would carry.
 */
final class SimulatedAuth {

    private SimulatedAuth() {
    }

    static <T> T runAs(UUID userId, Supplier<T> action) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(token);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
