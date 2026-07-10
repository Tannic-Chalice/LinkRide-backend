package com.linkride.backend.discovery.scoring;

/**
 * The multi-criteria cost/benefit breakdown for one {@link com.linkride.backend.discovery.corridor.CorridorMatchCandidate}
 * (design doc §6) — raw, real-unit components plus the weighted composite. Lower is better on
 * every field here, including {@code compositeScore}: every raw component is a cost to minimize
 * (or, for {@code benefitRatio}, inverted to {@code 1 - benefitRatio} before weighting), per §6.5.
 *
 * <p>{@code driverDetourDistanceMeters} is provably {@code 0} under this project's corridor-derived
 * pickup/drop (§5.2) — see {@link com.linkride.backend.discovery.MatchingProperties#getStopTimeSeconds()}'s
 * Javadoc for why — kept as an explicit field rather than omitted so the formula from §6.1 stays
 * visible and the field is ready to become meaningful if real off-route detour computation is
 * added later.</p>
 */
public record MatchScore(
        double driverDetourDistanceMeters,
        double driverDetourTimeSeconds,
        double passengerDetourRatio,
        double walkFraction,
        double benefitRatio,
        double timeWindowOffsetMinutes,
        double compositeScore) {
}
