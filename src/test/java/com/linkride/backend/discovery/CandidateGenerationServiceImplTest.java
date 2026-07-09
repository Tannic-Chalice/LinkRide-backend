package com.linkride.backend.discovery;

import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideStatus;
import com.linkride.backend.route.RouteGenerationState;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateGenerationServiceImplTest {

    @Mock
    private RideRepository rideRepository;

    private CandidateGenerationServiceImpl candidateGenerationService;

    @BeforeEach
    void setUp() {
        MatchingProperties matchingProperties = new MatchingProperties();
        matchingProperties.setDepartureWindowMinutes(90);
        candidateGenerationService = new CandidateGenerationServiceImpl(rideRepository, matchingProperties);
    }

    private TripSearchRequest request(OffsetDateTime desiredDepartureTime, int passengerCount) {
        TripSearchRequest request = new TripSearchRequest();
        request.setDesiredDepartureTime(desiredDepartureTime);
        request.setPassengerCount(passengerCount);
        return request;
    }

    private PassengerSearchContext context(UUID passengerId, TripSearchRequest request) {
        return PassengerSearchContext.builder()
                .passengerId(passengerId)
                .request(request)
                .passengerRoute(PassengerRoute.builder().build())
                .build();
    }

    @Test
    void generateCandidates_queriesRepositoryWithCoarseFiltersAndDepartureWindow() {
        UUID passengerId = UUID.randomUUID();
        OffsetDateTime desiredDeparture = OffsetDateTime.now().plusHours(2);
        TripSearchRequest request = request(desiredDeparture, 2);

        when(rideRepository.findCandidatesForSearch(anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        candidateGenerationService.generateCandidates(context(passengerId, request));

        ArgumentCaptor<OffsetDateTime> windowStartCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> windowEndCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(rideRepository).findCandidatesForSearch(
                eq(2),
                eq(RouteGenerationState.READY),
                eq(RideStatus.SCHEDULED),
                windowStartCaptor.capture(),
                windowEndCaptor.capture(),
                eq(passengerId));

        assertThat(windowStartCaptor.getValue()).isEqualTo(desiredDeparture.minusMinutes(90));
        assertThat(windowEndCaptor.getValue()).isEqualTo(desiredDeparture.plusMinutes(90));
    }

    @Test
    void generateCandidates_returnsWhateverRepositoryReturns() {
        Ride ride = new Ride();
        ride.setRideId(UUID.randomUUID());
        when(rideRepository.findCandidatesForSearch(anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(ride));

        List<Ride> result = candidateGenerationService.generateCandidates(
                context(UUID.randomUUID(), request(OffsetDateTime.now().plusHours(2), 1)));

        assertThat(result).containsExactly(ride);
    }
}
