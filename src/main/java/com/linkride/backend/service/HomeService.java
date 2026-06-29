package com.linkride.backend.service;

import com.linkride.backend.dto.favorite.FavoriteResponse;
import com.linkride.backend.dto.home.*;
import com.linkride.backend.entity.Favorite;
import com.linkride.backend.entity.User;
import com.linkride.backend.enums.RideCategoryId;
import com.linkride.backend.repository.FavoriteRepository;
import com.linkride.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Assembles the complete {@link HomeResponse} payload for {@code GET /api/home}.
 *
 * <p>All data required by the Home Screen is gathered here in a single
 * {@code @Transactional(readOnly = true)} call, keeping the controller thin
 * and ensuring Hibernate skips dirty-checking overhead.</p>
 *
 * <p><b>Stubs</b> — two methods are intentionally stubbed pending their modules:</p>
 * <ul>
 *   <li>{@link #resolveWalletBalance(UUID)} — returns {@code 0.00} until the Wallet
 *       module provides an aggregate query.</li>
 *   <li>{@link #resolveUnreadNotificationCount(UUID)} — returns {@code 0} until a
 *       {@code notifications} table is introduced.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserRepository     userRepository;
    private final FavoriteRepository favoriteRepository;

    // ── Constants ────────────────────────────────────────────────────────────

    private static final int     MAX_FAVORITES          = 6;
    private static final boolean ALLOW_CUSTOM_FAVORITES = true;

    /**
     * Popular destinations are static configuration until an admin panel is built.
     * When a content-management requirement arises, extract these to a
     * {@code popular_destinations} DB table with a separate admin CRUD API.
     */
    private static final List<PopularDestinationDto> POPULAR_DESTINATIONS = List.of(
        PopularDestinationDto.builder().id("LOC001").name("Kempegowda Airport").address("Devanahalli").build(),
        PopularDestinationDto.builder().id("LOC002").name("Majestic Bus Stand").address("K.G. Road").build(),
        PopularDestinationDto.builder().id("LOC003").name("Forum Mall").address("Koramangala").build()
    );

    private static final List<RideCategoryDto> RIDE_CATEGORIES = List.of(
        RideCategoryDto.builder().id(RideCategoryId.CAR).name("Car").icon("car").build(),
        RideCategoryDto.builder().id(RideCategoryId.BIKE).name("Bike").icon("bike").build(),
        RideCategoryDto.builder().id(RideCategoryId.AUTO).name("Auto").icon("auto").build()
    );

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Builds the full Home Screen payload for the given authenticated user.
     *
     * @param userId       UUID extracted from the Supabase JWT {@code sub} claim
     * @param latitude     device latitude supplied by the client
     * @param longitude    device longitude supplied by the client
     * @param locationName optional reverse-geocoded place name supplied by the client
     * @return assembled {@link HomeResponse}
     * @throws RuntimeException if the user is not found in the local DB
     */
    @Transactional(readOnly = true)
    public HomeResponse buildHomeResponse(UUID userId,
                                          Double latitude,
                                          Double longitude,
                                          String locationName) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<FavoriteResponse> favorites = favoriteRepository
            .findByUser_IdOrderByDisplayOrderAsc(userId)
            .stream()
            .map(this::toFavoriteResponse)
            .toList();

        BigDecimal walletBalance = resolveWalletBalance(userId);
        int        unreadCount   = resolveUnreadNotificationCount(userId);

        return HomeResponse.builder()
            .user(HomeUserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())   // Returned as-is — no string splitting
                .rating(user.getRating())
                .build())
            .currentLocation(CurrentLocationDto.builder()
                .latitude(latitude)
                .longitude(longitude)
                .name(locationName != null ? locationName : "")
                .build())
            .favorites(favorites)
            .popularDestinations(POPULAR_DESTINATIONS)
            .rideCategories(RIDE_CATEGORIES)
            .wallet(WalletSummaryDto.builder().balance(walletBalance).build())
            .notifications(NotificationSummaryDto.builder().unreadCount(unreadCount).build())
            .config(HomeConfigDto.builder()
                .maxFavorites(MAX_FAVORITES)
                .allowCustomFavorites(ALLOW_CUSTOM_FAVORITES)
                .build())
            .build();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private FavoriteResponse toFavoriteResponse(Favorite f) {
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

    /**
     * STUB — Computes the wallet balance as {@code SUM(CREDIT) - SUM(DEBIT)}
     * for {@code SUCCESS} transactions.
     *
     * <p>Replace with the following JPQL query once the Wallet module is built:</p>
     * <pre>
     * SELECT COALESCE(SUM(
     *   CASE WHEN transaction_type = 'CREDIT' THEN amount
     *        WHEN transaction_type = 'DEBIT'  THEN -amount
     *        ELSE 0 END), 0)
     * FROM wallet_transactions
     * WHERE user_id = :userId AND status = 'SUCCESS'
     * </pre>
     */
    private BigDecimal resolveWalletBalance(UUID userId) {
        return BigDecimal.ZERO;
    }

    /**
     * STUB — Returns the count of unread notifications.
     * Wire to a {@code notifications} table once the Notifications module is introduced.
     */
    private int resolveUnreadNotificationCount(UUID userId) {
        return 0;
    }
}
