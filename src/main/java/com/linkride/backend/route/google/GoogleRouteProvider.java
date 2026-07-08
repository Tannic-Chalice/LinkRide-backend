package com.linkride.backend.route.google;

import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.util.List;

/**
 * Production {@link RouteProvider} backed by the Google Directions API (DRIVING mode).
 * Pure orchestration: builds nothing itself beyond the retry loop — request construction lives
 * in {@link GoogleDirectionsClientImpl}, response interpretation in {@link GoogleRouteMapper}.
 *
 * <p>Retry only covers transport failures (timeout/connection) on the assumption that a
 * definitive response from Google (even an error status) is not worth retrying with identical
 * input. Default active {@link RouteProvider}; disable via {@code linkride.routing.provider=noop}.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "linkride.routing", name = "provider", havingValue = "google", matchIfMissing = true)
public class GoogleRouteProvider implements RouteProvider {

    private final GoogleDirectionsClient client;
    private final GoogleRouteMapper mapper;
    private final GoogleMapsProperties properties;

    public GoogleRouteProvider(GoogleDirectionsClient client, GoogleRouteMapper mapper, GoogleMapsProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public RouteResult computeRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination) {
        int totalAttempts = properties.getMaxRetries() + 1;
        RestClientException lastFailure = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                GoogleDirectionsResponse response = client.fetchRoute(pickup, waypoints, destination);
                return mapper.map(response);
            } catch (RestClientException e) {
                lastFailure = e;
                log.warn("Google Directions call failed (attempt {}/{}): {}", attempt, totalAttempts, e.getMessage());
                if (attempt < totalAttempts) {
                    sleepBackoff();
                }
            }
        }

        return RouteResult.pending(classifyFailure(lastFailure));
    }

    private RouteFailureReason classifyFailure(RestClientException e) {
        return e.getCause() instanceof SocketTimeoutException
                ? RouteFailureReason.TIMEOUT
                : RouteFailureReason.CONNECTION_FAILURE;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(properties.getRetryBackoff().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
