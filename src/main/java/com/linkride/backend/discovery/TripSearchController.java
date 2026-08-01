package com.linkride.backend.discovery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Trip Discovery Phase 2B.1 — passenger search API surface only.
 *
 * <p>This endpoint locks down the request/response contract, auth wiring, and validation for
 * passenger trip search, independently of the matching algorithm behind it. The full matching
 * pipeline — candidate generation, corridor computation, validation, scoring, and ranking — is
 * implemented in {@link TripSearchServiceImpl}; see {@link CandidateGenerationService} for where
 * the first stage of that pipeline lives.</p>
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The passenger's UUID is always resolved
 * from the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
public class TripSearchController {

    private final TripSearchService tripSearchService;

    @PostMapping("/search")
    public ResponseEntity<TripSearchResponse> search(
            @Valid @RequestBody TripSearchRequest request,
            Authentication authentication) {

        UUID passengerId = UUID.fromString(authentication.getName());
        TripSearchResponse response = tripSearchService.search(passengerId, request);
        return ResponseEntity.ok(response);
    }
}
