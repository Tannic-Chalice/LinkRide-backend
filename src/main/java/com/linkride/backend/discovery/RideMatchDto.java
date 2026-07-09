package com.linkride.backend.discovery;

import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * A single search result — a ride sharing a meaningful enough leg of the passenger's journey
 * (Phase 2B.5's {@code minOverlapFraction} gate), with pickup/drop derived from the shared
 * corridor. Not yet validated against hard constraints (Phase 2B.6: seat capacity re-check,
 * time-window, drop-before-destination) and not yet scored or ranked (Phase 2B.7–2B.8) — order in
 * {@link TripSearchResponse#getMatches()} is not meaningful yet, and a ride appearing here is not
 * a guarantee of a good match, only of a genuinely shared corridor.
 */
@Data
@Builder
public class RideMatchDto {

    private UUID rideId;
    private LatLngDto pickup;
    private LatLngDto drop;
    private double overlapFraction;

    public static RideMatchDto from(CorridorMatchCandidate candidate) {
        return RideMatchDto.builder()
                .rideId(candidate.ride().getRideId())
                .pickup(new LatLngDto(candidate.pickup().lat(), candidate.pickup().lng()))
                .drop(new LatLngDto(candidate.drop().lat(), candidate.drop().lng()))
                .overlapFraction(candidate.corridorRun().overlapFraction())
                .build();
    }
}
