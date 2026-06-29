package com.linkride.backend.dto.home;

import com.linkride.backend.dto.favorite.FavoriteResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Top-level response payload for {@code GET /api/home}.
 *
 * <p>Contains every piece of data the Home Screen needs to render in a single
 * network round-trip: user profile, current location, favourites, popular
 * destinations, ride categories, wallet summary, notification badge, and
 * client-side configuration.</p>
 */
@Data
@Builder
public class HomeResponse {
    private HomeUserDto                 user;
    private CurrentLocationDto          currentLocation;
    private List<FavoriteResponse>      favorites;
    private List<PopularDestinationDto> popularDestinations;
    private List<RideCategoryDto>       rideCategories;
    private WalletSummaryDto            wallet;
    private NotificationSummaryDto      notifications;
    private HomeConfigDto               config;
}
