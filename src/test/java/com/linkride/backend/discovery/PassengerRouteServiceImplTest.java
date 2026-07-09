package com.linkride.backend.discovery;

import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import com.linkride.backend.route.geometry.RouteGeometryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassengerRouteServiceImplTest {

    @Mock
    private RouteProvider routeProvider;

    private PassengerRouteServiceImpl passengerRouteService;

    @BeforeEach
    void setUp() {
        // Real mapper (and its own real geometry builder): pure, dependency-free logic — mocking
        // it would just add noise. Its own behavior is covered separately by PassengerRouteMapperTest.
        passengerRouteService = new PassengerRouteServiceImpl(routeProvider, new PassengerRouteMapper(new RouteGeometryBuilder()));
    }

    private GeoPointDto geoPoint(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("Test Location");
        dto.setAddress("123 Test Street");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    @Test
    void computeRoute_delegatesToRouteProviderWithNoWaypoints() {
        GeoPointDto origin = geoPoint(12.9716, 77.5946);
        GeoPointDto destination = geoPoint(13.1986, 77.7066);
        TripSearchRequest request = new TripSearchRequest();
        request.setOrigin(origin);
        request.setDestination(destination);

        when(routeProvider.computeRoute(eq(origin), any(), eq(destination)))
                .thenReturn(RouteResult.ready("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5000, 600));

        PassengerRoute result = passengerRouteService.computeRoute(request);

        assertThat(result.getPolyline()).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");

        ArgumentCaptor<List<GeoPointDto>> waypointsCaptor = ArgumentCaptor.forClass(List.class);
        verify(routeProvider).computeRoute(eq(origin), waypointsCaptor.capture(), eq(destination));
        assertThat(waypointsCaptor.getValue()).isEmpty();
    }

    @Test
    void computeRoute_delegatesMappingToPassengerRouteMapper() {
        TripSearchRequest request = new TripSearchRequest();
        request.setOrigin(geoPoint(12.9716, 77.5946));
        request.setDestination(geoPoint(13.1986, 77.7066));

        when(routeProvider.computeRoute(any(), any(), any())).thenReturn(RouteResult.pending(null));

        PassengerRoute result = passengerRouteService.computeRoute(request);

        assertThat(result.getRouteGenerationState()).isEqualTo(RouteGenerationState.PENDING);
    }
}
