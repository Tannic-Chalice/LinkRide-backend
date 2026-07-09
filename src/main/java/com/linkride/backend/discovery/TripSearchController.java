package com.linkride.backend.discovery;

import com.linkride.backend.dto.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Trip Discovery Phase 2B.1 — passenger search API surface only.
 *
 * <p>This endpoint locks down the request/response contract, auth wiring, and validation for
 * passenger trip search, independently of the matching algorithm behind it. Route generation
 * (2B.2) through ranking (2B.8) land in later milestones, so {@link TripSearchService} currently
 * always returns an empty match list — see {@link TripSearchServiceImpl} and
 * {@link CandidateGenerationService} for where that seam lives.</p>
 *
 * <p>Security: all routes require a valid Supabase JWT (enforced globally by
 * {@link com.linkride.backend.config.SecurityConfig}). The passenger's UUID is always resolved
 * from the JWT — never from the request body.</p>
 */
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class TripSearchController {

    private final TripSearchService tripSearchService;

    @PostMapping("/search")
    public ResponseEntity<?> search(
            @Valid @RequestBody TripSearchRequest request,
            Authentication authentication) {

        try {
            UUID passengerId = UUID.fromString(authentication.getName());
            TripSearchResponse response = tripSearchService.search(passengerId, request);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ErrorResponse(e.getStatusCode().toString(), e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }
}
