package com.linkride.backend.route.google;

import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.route.RouteFailureReason;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleRouteProviderTest {

    @Mock
    private GoogleDirectionsClient client;

    private GoogleRouteProvider provider;

    @BeforeEach
    void setUp() {
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setMaxRetries(2);
        properties.setRetryBackoff(Duration.ofMillis(1));
        provider = new GoogleRouteProvider(client, new GoogleRouteMapper(), properties);
    }

    private GeoPointDto point(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("Test");
        dto.setAddress("Test address");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    private GoogleDirectionsResponse okResponseWithRoute() {
        GoogleDirectionsResponse response = new GoogleDirectionsResponse();
        response.setStatus("OK");

        GoogleDirectionsResponse.ValueField distance = new GoogleDirectionsResponse.ValueField();
        distance.setValue(1000);
        GoogleDirectionsResponse.ValueField duration = new GoogleDirectionsResponse.ValueField();
        duration.setValue(120);

        GoogleDirectionsResponse.Leg leg = new GoogleDirectionsResponse.Leg();
        leg.setDistance(distance);
        leg.setDuration(duration);

        GoogleDirectionsResponse.OverviewPolyline polyline = new GoogleDirectionsResponse.OverviewPolyline();
        polyline.setPoints("poly");

        GoogleDirectionsResponse.Route route = new GoogleDirectionsResponse.Route();
        route.setOverviewPolyline(polyline);
        route.setLegs(List.of(leg));

        response.setRoutes(List.of(route));
        return response;
    }

    @Test
    void returnsReadyOnSuccessfulFirstAttempt() {
        when(client.fetchRoute(any(), any(), any())).thenReturn(okResponseWithRoute());

        RouteResult result = provider.computeRoute(point(1, 1), List.of(), point(2, 2));

        assertThat(result.getState()).isEqualTo(RouteGenerationState.READY);
        verify(client, times(1)).fetchRoute(any(), any(), any());
    }

    @Test
    void retriesTransientTransportFailureThenSucceeds() {
        GoogleDirectionsResponse zeroResults = new GoogleDirectionsResponse();
        zeroResults.setStatus("ZERO_RESULTS");

        when(client.fetchRoute(any(), any(), any()))
                .thenThrow(new ResourceAccessException("boom", new SocketTimeoutException()))
                .thenReturn(zeroResults);

        RouteResult result = provider.computeRoute(point(1, 1), List.of(), point(2, 2));

        assertThat(result.getState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.ZERO_RESULTS);
        verify(client, times(2)).fetchRoute(any(), any(), any());
    }

    @Test
    void exhaustsRetriesAndReturnsPendingWithTimeoutReason() {
        when(client.fetchRoute(any(), any(), any()))
                .thenThrow(new ResourceAccessException("boom", new SocketTimeoutException()));

        RouteResult result = provider.computeRoute(point(1, 1), List.of(), point(2, 2));

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.TIMEOUT);
        verify(client, times(3)).fetchRoute(any(), any(), any());
    }

    @Test
    void exhaustsRetriesAndReturnsPendingWithConnectionFailureReason() {
        when(client.fetchRoute(any(), any(), any()))
                .thenThrow(new ResourceAccessException("boom", new ConnectException()));

        RouteResult result = provider.computeRoute(point(1, 1), List.of(), point(2, 2));

        assertThat(result.getState()).isEqualTo(RouteGenerationState.PENDING);
        assertThat(result.getFailureReason()).isEqualTo(RouteFailureReason.CONNECTION_FAILURE);
    }
}
