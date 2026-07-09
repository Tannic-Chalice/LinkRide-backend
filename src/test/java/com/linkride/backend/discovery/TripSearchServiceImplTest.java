package com.linkride.backend.discovery;

import com.linkride.backend.discovery.corridor.CorridorGenerationService;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.discovery.corridor.CorridorRun;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSearchServiceImplTest {

    @Mock
    private TripSearchRequestValidator validator;
    @Mock
    private PassengerRouteService passengerRouteService;
    @Mock
    private CandidateGenerationService candidateGenerationService;
    @Mock
    private CorridorGenerationService corridorGenerationService;

    private TripSearchServiceImpl tripSearchService;

    @BeforeEach
    void setUp() {
        tripSearchService = new TripSearchServiceImpl(
                validator, passengerRouteService, candidateGenerationService, corridorGenerationService);

        lenient().when(passengerRouteService.computeRoute(any()))
                .thenReturn(PassengerRoute.builder().routeGenerationState(RouteGenerationState.PENDING).build());
        lenient().when(candidateGenerationService.generateCandidates(any())).thenReturn(List.of());
        lenient().when(corridorGenerationService.generateCorridors(any(), any())).thenReturn(List.of());
    }

    private TripSearchRequest request() {
        TripSearchRequest request = new TripSearchRequest();
        request.setDesiredDepartureTime(OffsetDateTime.now().plusHours(2));
        request.setPassengerCount(1);
        return request;
    }

    @Test
    void search_delegatesValidationBeforeComputingRouteOrGeneratingCandidates() {
        tripSearchService.search(UUID.randomUUID(), request());

        verify(validator).validate(any());
    }

    @Test
    void search_invalidRequest_propagatesValidatorExceptionWithoutComputingRouteOrCandidates() {
        TripSearchRequest request = request();
        doThrow(new IllegalArgumentException("Origin and destination must not be the same location"))
                .when(validator).validate(request);

        assertThatThrownBy(() -> tripSearchService.search(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(passengerRouteService);
        verifyNoInteractions(candidateGenerationService);
        verifyNoInteractions(corridorGenerationService);
    }

    @Test
    void search_assemblesContextWithPassengerIdRequestAndRouteForCandidateGeneration() {
        PassengerRoute passengerRoute = PassengerRoute.builder()
                .polyline("encodedPolyline")
                .distanceMeters(5000)
                .durationSeconds(600)
                .routeGenerationState(RouteGenerationState.READY)
                .build();
        when(passengerRouteService.computeRoute(any())).thenReturn(passengerRoute);
        TripSearchRequest request = request();
        UUID passengerId = UUID.randomUUID();

        TripSearchResponse response = tripSearchService.search(passengerId, request);

        assertThat(response.getPassengerRoute().getRouteGenerationState()).isEqualTo(RouteGenerationState.READY);
        assertThat(response.getPassengerRoute().getPolyline()).isEqualTo("encodedPolyline");
        assertThat(response.getPassengerRoute().getDistanceMeters()).isEqualTo(5000);
        assertThat(response.getPassengerRoute().getDurationSeconds()).isEqualTo(600);

        ArgumentCaptor<PassengerSearchContext> contextCaptor = ArgumentCaptor.forClass(PassengerSearchContext.class);
        verify(candidateGenerationService).generateCandidates(contextCaptor.capture());
        PassengerSearchContext context = contextCaptor.getValue();
        assertThat(context.getPassengerId()).isEqualTo(passengerId);
        assertThat(context.getRequest()).isEqualTo(request);
        assertThat(context.getPassengerRoute()).isEqualTo(passengerRoute);
    }

    @Test
    void search_passesCandidatesAndSameContextToCorridorGeneration() {
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        List<Ride> candidates = List.of(ride);
        when(candidateGenerationService.generateCandidates(any())).thenReturn(candidates);

        tripSearchService.search(UUID.randomUUID(), request());

        ArgumentCaptor<PassengerSearchContext> contextCaptor = ArgumentCaptor.forClass(PassengerSearchContext.class);
        verify(corridorGenerationService).generateCorridors(contextCaptor.capture(), eq(candidates));

        ArgumentCaptor<PassengerSearchContext> candidateGenContextCaptor = ArgumentCaptor.forClass(PassengerSearchContext.class);
        verify(candidateGenerationService).generateCandidates(candidateGenContextCaptor.capture());
        assertThat(contextCaptor.getValue()).isEqualTo(candidateGenContextCaptor.getValue());
    }

    @Test
    void search_routeUnroutable_stillReturnsResponseWithUnroutableState() {
        PassengerRoute passengerRoute = PassengerRoute.builder()
                .routeGenerationState(RouteGenerationState.UNROUTABLE)
                .build();
        when(passengerRouteService.computeRoute(any())).thenReturn(passengerRoute);

        TripSearchResponse response = tripSearchService.search(UUID.randomUUID(), request());

        assertThat(response.getPassengerRoute().getRouteGenerationState()).isEqualTo(RouteGenerationState.UNROUTABLE);
        assertThat(response.getPassengerRoute().getPolyline()).isNull();
        assertThat(response.getMatches()).isEmpty();
    }

    @Test
    void search_noCorridorMatches_returnsEmptyMatchList() {
        TripSearchResponse response = tripSearchService.search(UUID.randomUUID(), request());

        assertThat(response.getMatches()).isEmpty();
    }

    @Test
    void search_corridorMatchesPresent_mapsToRideMatchDtos() {
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        RoutePoint entry = new RoutePoint(12.9716, 77.5946, 0, 0, 0);
        RoutePoint exit = new RoutePoint(12.9800, 77.6000, 500, 60, 1);
        CorridorRun run = new CorridorRun(entry, exit, 0.5);
        NearestPointOnRoute pickup = new NearestPointOnRoute(12.9720, 77.5950, 10, 15);
        NearestPointOnRoute drop = new NearestPointOnRoute(12.9795, 77.5995, 490, 25);
        CorridorMatchCandidate candidate = new CorridorMatchCandidate(ride, run, pickup, drop);

        when(corridorGenerationService.generateCorridors(any(), any())).thenReturn(List.of(candidate));

        TripSearchResponse response = tripSearchService.search(UUID.randomUUID(), request());

        assertThat(response.getMatches()).hasSize(1);
        RideMatchDto match = response.getMatches().get(0);
        assertThat(match.getRideId()).isEqualTo(ride.getRideId());
        assertThat(match.getPickup().getLatitude()).isEqualTo(12.9720);
        assertThat(match.getPickup().getLongitude()).isEqualTo(77.5950);
        assertThat(match.getDrop().getLatitude()).isEqualTo(12.9795);
        assertThat(match.getDrop().getLongitude()).isEqualTo(77.5995);
        assertThat(match.getOverlapFraction()).isEqualTo(0.5);
    }
}
