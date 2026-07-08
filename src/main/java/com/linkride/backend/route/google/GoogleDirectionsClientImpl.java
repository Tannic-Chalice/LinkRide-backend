package com.linkride.backend.route.google;

import com.linkride.backend.location.GeoPointDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoogleDirectionsClientImpl implements GoogleDirectionsClient {

    private final RestClient restClient;
    private final String apiKey;

    public GoogleDirectionsClientImpl(RestClient googleDirectionsRestClient, GoogleMapsProperties properties) {
        this.restClient = googleDirectionsRestClient;
        this.apiKey = properties.getApiKey();
    }

    @Override
    public GoogleDirectionsResponse fetchRoute(GeoPointDto pickup, List<GeoPointDto> waypoints, GeoPointDto destination) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.queryParam("origin", toLatLng(pickup))
                            .queryParam("destination", toLatLng(destination))
                            .queryParam("mode", "driving")
                            .queryParam("key", apiKey);
                    if (waypoints != null && !waypoints.isEmpty()) {
                        String waypointsParam = waypoints.stream()
                                .map(this::toLatLng)
                                .collect(Collectors.joining("|"));
                        uriBuilder.queryParam("waypoints", waypointsParam);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(GoogleDirectionsResponse.class);
    }

    private String toLatLng(GeoPointDto point) {
        return point.getLatitude() + "," + point.getLongitude();
    }
}
