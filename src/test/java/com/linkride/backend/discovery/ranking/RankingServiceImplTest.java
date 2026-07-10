package com.linkride.backend.discovery.ranking;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.discovery.corridor.CorridorRun;
import com.linkride.backend.discovery.scoring.MatchScore;
import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RoutePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RankingServiceImplTest {

    private MatchingProperties matchingProperties;
    private RankingServiceImpl rankingService;

    @BeforeEach
    void setUp() {
        matchingProperties = new MatchingProperties();
        rankingService = new RankingServiceImpl(matchingProperties);
    }

    private Ride ride(UUID rideId) {
        Ride ride = new Ride();
        ride.setRideId(rideId);
        return ride;
    }

    private ScoredMatchCandidate scored(UUID rideId, MatchScore score) {
        RoutePoint entry = new RoutePoint(12.9716, 77.5946, 0, 0, 0);
        RoutePoint exit = new RoutePoint(12.9800, 77.6000, 500, 60, 1);
        CorridorRun run = new CorridorRun(entry, exit, 0.5);
        NearestPointOnRoute pickup = new NearestPointOnRoute(12.9720, 77.5950, 10, 15);
        NearestPointOnRoute drop = new NearestPointOnRoute(12.9795, 77.5995, 490, 25);
        CorridorMatchCandidate candidate = new CorridorMatchCandidate(ride(rideId), run, pickup, drop);
        return new ScoredMatchCandidate(candidate, score);
    }

    private MatchScore score(double compositeScore) {
        return new MatchScore(0, 180, 1.0, 0.2, 0.6, 5, compositeScore);
    }

    private PassengerSearchContext context() {
        return PassengerSearchContext.builder()
                .passengerId(UUID.randomUUID())
                .request(new TripSearchRequest())
                .build();
    }

    @Test
    void rank_emptyInput_returnsEmpty() {
        assertThat(rankingService.rank(context(), List.of())).isEmpty();
    }

    @Test
    void rank_sortsByCompositeScoreAscending() {
        UUID worseId = UUID.randomUUID();
        UUID betterId = UUID.randomUUID();
        ScoredMatchCandidate worse = scored(worseId, score(0.8));
        ScoredMatchCandidate better = scored(betterId, score(0.2));

        List<RankedMatchCandidate> result = rankingService.rank(context(), List.of(worse, better));

        assertThat(result).extracting(r -> r.scored().match().ride().getRideId())
                .containsExactly(betterId, worseId);
    }

    @Test
    void rank_tieBreaksByDriverDetourTimeWhenCompositeScoreEqual() {
        UUID lowerDetourId = UUID.randomUUID();
        UUID higherDetourId = UUID.randomUUID();
        ScoredMatchCandidate higherDetour = scored(higherDetourId, new MatchScore(0, 300, 1.0, 0.2, 0.6, 5, 0.5));
        ScoredMatchCandidate lowerDetour = scored(lowerDetourId, new MatchScore(0, 100, 1.0, 0.2, 0.6, 5, 0.5));

        List<RankedMatchCandidate> result = rankingService.rank(context(), List.of(higherDetour, lowerDetour));

        assertThat(result).extracting(r -> r.scored().match().ride().getRideId())
                .containsExactly(lowerDetourId, higherDetourId);
    }

    @Test
    void rank_tieBreaksByRideIdWhenEverythingElseEqual() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ScoredMatchCandidate a = scored(higherId, score(0.5));
        ScoredMatchCandidate b = scored(lowerId, score(0.5));

        List<RankedMatchCandidate> result = rankingService.rank(context(), List.of(a, b));

        assertThat(result).extracting(r -> r.scored().match().ride().getRideId())
                .containsExactly(lowerId, higherId);
    }

    @Test
    void rank_truncatesToConfiguredLimit() {
        matchingProperties.setMaxRankedMatches(2);
        List<ScoredMatchCandidate> candidates = List.of(
                scored(UUID.randomUUID(), score(0.1)),
                scored(UUID.randomUUID(), score(0.2)),
                scored(UUID.randomUUID(), score(0.3)));

        List<RankedMatchCandidate> result = rankingService.rank(context(), candidates);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(r -> r.scored().score().compositeScore())
                .containsExactly(0.1, 0.2);
    }

    @Test
    void rank_nonDominatedCandidates_bothMarkedParetoOptimal() {
        // A: better driver-detour-time, worse walk fraction. B: the reverse. Neither dominates.
        ScoredMatchCandidate a = scored(UUID.randomUUID(), new MatchScore(0, 100, 1.0, 0.5, 0.6, 5, 0.4));
        ScoredMatchCandidate b = scored(UUID.randomUUID(), new MatchScore(0, 300, 1.0, 0.1, 0.6, 5, 0.4));

        List<RankedMatchCandidate> result = rankingService.rank(context(), List.of(a, b));

        assertThat(result).extracting(RankedMatchCandidate::paretoOptimal).containsExactly(true, true);
    }

    @Test
    void rank_dominatedCandidate_notMarkedParetoOptimal() {
        UUID dominatedId = UUID.randomUUID();
        UUID dominatorId = UUID.randomUUID();
        // dominator is at least as good on every component and strictly better on driverDetourTime.
        ScoredMatchCandidate dominator = scored(dominatorId, new MatchScore(0, 100, 1.0, 0.2, 0.6, 5, 0.3));
        ScoredMatchCandidate dominated = scored(dominatedId, new MatchScore(0, 300, 1.0, 0.2, 0.6, 5, 0.5));

        List<RankedMatchCandidate> result = rankingService.rank(context(), List.of(dominator, dominated));

        assertThat(result).extracting(
                        r -> r.scored().match().ride().getRideId(), RankedMatchCandidate::paretoOptimal)
                .containsExactly(tuple(dominatorId, true), tuple(dominatedId, false));
    }
}
