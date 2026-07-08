package com.linkride.backend.route.google;

import com.linkride.backend.location.GeoPointDto;

import java.util.List;

/**
 * Raw transport call to the Google Directions API. Throws {@link org.springframework.web.client.RestClientException}
 * (unchecked) on transport failure — {@link GoogleRouteProvider} owns retry/backoff around this.
 */
public interface GoogleDirectionsClient {
    GoogleDirectionsResponse fetchRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination);
}
