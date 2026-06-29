package com.linkride.backend.service;

import com.linkride.backend.dto.favorite.FavoriteReorderItem;
import com.linkride.backend.dto.favorite.FavoriteRequest;
import com.linkride.backend.dto.favorite.FavoriteResponse;
import com.linkride.backend.entity.Favorite;
import com.linkride.backend.entity.User;
import com.linkride.backend.enums.FavoriteType;
import com.linkride.backend.repository.FavoriteRepository;
import com.linkride.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for managing a user's favourite locations.
 *
 * <h3>Business rules enforced here</h3>
 * <ul>
 *   <li>Maximum of {@value #MAX_FAVORITES} favourites per user.</li>
 *   <li>At most one {@code HOME} and one {@code WORK} favourite per user.</li>
 *   <li>Only the owner may update or delete a favourite (ownership validated
 *       via {@link FavoriteRepository#findByFavoriteIdAndUser_Id}).</li>
 *   <li>Reorder list must be complete (all favourites included) and use a
 *       contiguous 1..N {@code displayOrder} sequence.</li>
 * </ul>
 *
 * <p>All rules are also backed by DB-level constraints (partial unique indexes,
 * the display_order unique index) defined in the migration SQL.</p>
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private static final int MAX_FAVORITES = 6;

    private final FavoriteRepository favoriteRepository;
    private final UserRepository     userRepository;

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a new favourite for the authenticated user.
     *
     * <p>{@code displayOrder} is auto-assigned to {@code (currentMax + 1)} so
     * the new favourite always appears at the end of the user's list.</p>
     *
     * @throws IllegalStateException if the user has reached the max cap or already
     *                               has a HOME/WORK favourite of the requested type
     * @throws RuntimeException      if the user is not found in the local DB
     */
    @Transactional
    public FavoriteResponse addFavorite(UUID userId, FavoriteRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Rule 1: enforce total cap
        long count = favoriteRepository.countByUser_Id(userId);
        if (count >= MAX_FAVORITES) {
            throw new IllegalStateException(
                "Maximum of " + MAX_FAVORITES + " favorites allowed. Please remove one first.");
        }

        // Rule 2: enforce singleton HOME / WORK types
        if (request.getType() == FavoriteType.HOME || request.getType() == FavoriteType.WORK) {
            if (favoriteRepository.existsByUser_IdAndType(userId, request.getType())) {
                throw new IllegalStateException(
                    "A " + request.getType().name() + " favorite already exists. " +
                    "Update or delete it before adding a new one.");
            }
        }

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        // Auto-assign displayOrder: appends to the end of the list
        favorite.setDisplayOrder(favoriteRepository.findMaxDisplayOrderByUserId(userId) + 1);
        applyRequest(favorite, request);

        return toResponse(favoriteRepository.save(favorite));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    /**
     * Updates the label, type, address, coordinates, icon, and pin state of a favourite.
     *
     * <p>{@code displayOrder} is intentionally NOT updated here — use
     * {@code PATCH /api/favorites/reorder} to change list order.</p>
     *
     * @throws IllegalStateException if changing type would create a duplicate HOME/WORK
     * @throws RuntimeException      if the favourite is not found or not owned by the user
     */
    @Transactional
    public FavoriteResponse updateFavorite(UUID userId, UUID favoriteId, FavoriteRequest request) {
        Favorite favorite = favoriteRepository.findByFavoriteIdAndUser_Id(favoriteId, userId)
            .orElseThrow(() -> new RuntimeException("Favorite not found or access denied"));

        // If the type is changing to HOME/WORK, ensure no duplicate exists (excluding self)
        if ((request.getType() == FavoriteType.HOME || request.getType() == FavoriteType.WORK)
                && request.getType() != favorite.getType()) {
            if (favoriteRepository.existsByUser_IdAndType(userId, request.getType())) {
                throw new IllegalStateException(
                    "A " + request.getType().name() + " favorite already exists.");
            }
        }

        applyRequest(favorite, request);
        return toResponse(favoriteRepository.save(favorite));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes a favourite.
     *
     * <p>Note on gaps: deletion may leave a gap in {@code displayOrder}
     * (e.g. 1, 3 after deleting position 2). The gap is harmless for
     * rendering — the UI sorts ascending. If the client wants a clean 1..N
     * sequence after deletion, it should call {@code PATCH /api/favorites/reorder}
     * with the remaining items renumbered.</p>
     *
     * @throws RuntimeException if the favourite is not found or not owned by the user
     */
    @Transactional
    public void deleteFavorite(UUID userId, UUID favoriteId) {
        Favorite favorite = favoriteRepository.findByFavoriteIdAndUser_Id(favoriteId, userId)
            .orElseThrow(() -> new RuntimeException("Favorite not found or access denied"));
        favoriteRepository.delete(favorite);
    }

    // ── Reorder ──────────────────────────────────────────────────────────────

    /**
     * Bulk-updates {@code displayOrder} for all of a user's favourites in one
     * atomic transaction.
     *
     * <h3>Validation rules</h3>
     * <ol>
     *   <li>Every {@code favoriteId} in the request must belong to the authenticated user.</li>
     *   <li>The submitted list must contain exactly as many items as the user currently has
     *       (no additions, no omissions — prevents partial reorders).</li>
     *   <li>{@code displayOrder} values must form a contiguous sequence {@code 1..N}
     *       with no gaps or duplicates.</li>
     * </ol>
     *
     * @return the full sorted list of favourites after the reorder is applied
     * @throws IllegalStateException if the list size is wrong or orders are non-contiguous
     * @throws RuntimeException      if any {@code favoriteId} is not owned by the user
     */
    @Transactional
    public List<FavoriteResponse> reorderFavorites(UUID userId, List<FavoriteReorderItem> items) {
        List<Favorite> existingFavorites =
            favoriteRepository.findByUser_IdOrderByDisplayOrderAsc(userId);

        // Rule 2: submitted list must exactly match the user's current favourites
        if (items.size() != existingFavorites.size()) {
            throw new IllegalStateException(
                "Reorder list must include all " + existingFavorites.size() + " favorites.");
        }

        // Rule 3: displayOrder values must be exactly 1..N (sorted check)
        List<Integer> submittedOrders = items.stream()
            .map(FavoriteReorderItem::getDisplayOrder)
            .sorted()
            .toList();
        for (int i = 0; i < submittedOrders.size(); i++) {
            if (submittedOrders.get(i) != i + 1) {
                throw new IllegalStateException(
                    "displayOrder values must be a contiguous sequence starting at 1.");
            }
        }

        // Build O(1) lookup map keyed by favoriteId
        Map<UUID, Favorite> favoriteMap = existingFavorites.stream()
            .collect(Collectors.toMap(Favorite::getFavoriteId, f -> f));

        for (FavoriteReorderItem item : items) {
            Favorite favorite = favoriteMap.get(item.getFavoriteId());
            if (favorite == null) {
                // Rule 1: favoriteId does not belong to this user
                throw new RuntimeException(
                    "Favorite " + item.getFavoriteId() + " not found or access denied");
            }
            favorite.setDisplayOrder(item.getDisplayOrder());
        }

        return favoriteRepository.saveAll(existingFavorites).stream()
            .sorted(Comparator.comparingInt(Favorite::getDisplayOrder))
            .map(this::toResponse)
            .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Applies all mutable fields from the request onto the entity. Does NOT touch displayOrder. */
    private void applyRequest(Favorite favorite, FavoriteRequest request) {
        favorite.setLabel(request.getLabel().trim());
        favorite.setType(request.getType());
        favorite.setAddress(request.getAddress().trim());
        favorite.setLatitude(request.getLatitude());
        favorite.setLongitude(request.getLongitude());
        favorite.setIcon(request.getIcon());
        favorite.setIsPinned(request.getIsPinned() != null ? request.getIsPinned() : false);
    }

    private FavoriteResponse toResponse(Favorite f) {
        return FavoriteResponse.builder()
            .favoriteId(f.getFavoriteId())
            .label(f.getLabel())
            .type(f.getType())
            .address(f.getAddress())
            .latitude(f.getLatitude())
            .longitude(f.getLongitude())
            .icon(f.getIcon())
            .isPinned(f.getIsPinned())
            .displayOrder(f.getDisplayOrder())
            .build();
    }
}
