package com.linkride.backend.discovery;

import com.linkride.backend.discovery.ranking.RankedMatchCandidate;
import com.linkride.backend.discovery.scoring.ScoredMatchCandidate;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * A single search result — a ride sharing a meaningful enough leg of the passenger's journey
 * (Phase 2B.5's {@code minOverlapFraction} gate), with pickup/drop derived from the shared
 * corridor, surviving hard-constraint validation (Phase 2B.6: seat capacity re-check, pickup
 * time-window, drop-before-destination), carrying a scoring breakdown (Phase 2B.7, design doc §6
 * — {@code score} is a cost figure, lower is better), and ranked (Phase 2B.8, design doc §7).
 * Order in {@link TripSearchResponse#getMatches()} is now meaningful — best match first, sorted
 * by {@code score} ascending with documented tie-breaks — and the list is truncated to the
 * configured top-K, so a ride appearing here is one of the best available, not merely a feasible
 * one. {@code paretoOptimal} flags whether any other returned match is at least as good on every
 * cost/benefit component and strictly better on at least one (§7.1).
 */
@Data
@Builder
public class RideMatchDto {

    private UUID rideId;
    private LatLngDto pickup;
    private LatLngDto drop;
    private double overlapFraction;
    private double driverDetourTimeSeconds;
    private double passengerDetourRatio;
    private double walkFraction;
    private double benefitRatio;
    private double timeWindowOffsetMinutes;
    private double score;
    private boolean paretoOptimal;

    public static RideMatchDto from(RankedMatchCandidate ranked) {
        ScoredMatchCandidate scored = ranked.scored();
        return RideMatchDto.builder()
                .rideId(scored.match().ride().getRideId())
                .pickup(new LatLngDto(scored.match().pickup().lat(), scored.match().pickup().lng()))
                .drop(new LatLngDto(scored.match().drop().lat(), scored.match().drop().lng()))
                .overlapFraction(scored.match().corridorRun().overlapFraction())
                .driverDetourTimeSeconds(scored.score().driverDetourTimeSeconds())
                .passengerDetourRatio(scored.score().passengerDetourRatio())
                .walkFraction(scored.score().walkFraction())
                .benefitRatio(scored.score().benefitRatio())
                .timeWindowOffsetMinutes(scored.score().timeWindowOffsetMinutes())
                .score(scored.score().compositeScore())
                .paretoOptimal(ranked.paretoOptimal())
                .build();
    }
}
