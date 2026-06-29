package com.linkride.backend.controller;

import com.linkride.backend.dto.ErrorResponse;
import com.linkride.backend.dto.favorite.FavoriteReorderItem;
import com.linkride.backend.dto.favorite.FavoriteRequest;
import com.linkride.backend.dto.favorite.FavoriteResponse;
import com.linkride.backend.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Manages a user's favourite locations.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/favorites}          — create a new favourite</li>
 *   <li>{@code PUT    /api/favorites/{id}}      — update an existing favourite</li>
 *   <li>{@code DELETE /api/favorites/{id}}      — delete a favourite</li>
 *   <li>{@code PATCH  /api/favorites/reorder}   — bulk-reorder all favourites</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>All routes require a valid Supabase JWT. The authenticated user's UUID is
 * always resolved from the JWT — never from the request body — ensuring users
 * can only modify their own data.</p>
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    // ── POST /api/favorites ──────────────────────────────────────────────────

    /**
     * Creates a new favourite for the authenticated user.
     *
     * <p>Responds with {@code 201 Created} and the saved favourite object.
     * Returns {@code 400 Bad Request} for validation errors or business rule
     * violations (cap reached, duplicate HOME/WORK type).</p>
     */
    @PostMapping
    public ResponseEntity<?> addFavorite(
            @Valid @RequestBody FavoriteRequest request,
            Authentication authentication) {

        try {
            UUID userId = UUID.fromString(authentication.getName());
            FavoriteResponse response = favoriteService.addFavorite(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    // ── PUT /api/favorites/{favoriteId} ─────────────────────────────────────

    /**
     * Updates the label, type, address, coordinates, icon, and pin state of a favourite.
     *
     * <p>Note: {@code displayOrder} is NOT updated via this endpoint.
     * Use {@code PATCH /api/favorites/reorder} to change list order.</p>
     *
     * <p>Returns {@code 404 Not Found} if the favourite does not exist or does
     * not belong to the authenticated user.</p>
     */
    @PutMapping("/{favoriteId}")
    public ResponseEntity<?> updateFavorite(
            @PathVariable UUID favoriteId,
            @Valid @RequestBody FavoriteRequest request,
            Authentication authentication) {

        try {
            UUID userId = UUID.fromString(authentication.getName());
            FavoriteResponse response = favoriteService.updateFavorite(userId, favoriteId, request);
            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("FAVORITE_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    // ── DELETE /api/favorites/{favoriteId} ──────────────────────────────────

    /**
     * Deletes a favourite.
     *
     * <p>Returns {@code 204 No Content} on success (empty body).
     * Returns {@code 404 Not Found} if the favourite does not exist or does
     * not belong to the authenticated user.</p>
     */
    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<?> deleteFavorite(
            @PathVariable UUID favoriteId,
            Authentication authentication) {

        try {
            UUID userId = UUID.fromString(authentication.getName());
            favoriteService.deleteFavorite(userId, favoriteId);
            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("FAVORITE_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }

    // ── PATCH /api/favorites/reorder ─────────────────────────────────────────

    /**
     * Reorders all of the authenticated user's favourites in a single atomic transaction.
     *
     * <p>The client submits the complete list of {@code {favoriteId, displayOrder}} pairs
     * with the new desired positions. The backend validates that:</p>
     * <ol>
     *   <li>All existing favourites are present (no additions or omissions).</li>
     *   <li>{@code displayOrder} values form a contiguous 1..N sequence.</li>
     *   <li>Every {@code favoriteId} belongs to the authenticated user.</li>
     * </ol>
     *
     * <p>Returns {@code 200 OK} with the full favourites list sorted by the
     * new {@code displayOrder}.</p>
     */
    @PatchMapping("/reorder")
    public ResponseEntity<?> reorderFavorites(
            @Valid @RequestBody List<FavoriteReorderItem> items,
            Authentication authentication) {

        try {
            UUID userId = UUID.fromString(authentication.getName());
            List<FavoriteResponse> reordered = favoriteService.reorderFavorites(userId, items);
            return ResponseEntity.ok(reordered);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REORDER", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("FAVORITE_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
        }
    }
}
