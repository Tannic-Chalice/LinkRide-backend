package com.linkride.backend.discovery.scoring;

import com.linkride.backend.discovery.MatchingProperties;
import com.linkride.backend.discovery.PassengerSearchContext;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.corridor.CorridorMatchCandidate;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.route.geometry.GeoMath;
import com.linkride.backend.route.geometry.NearestPointOnRoute;
import com.linkride.backend.route.geometry.RouteEtaCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Phase 2B.7 implementation of design doc §6. Two passes over the validated candidate set:
 *
 * <ol>
 *   <li>Compute each candidate's raw-unit metrics, rejecting outright any candidate whose
 *   {@code passengerDetourRatio} exceeds {@link MatchingProperties#getMaxPassengerDetourRatio()}
 *   (§6.2a — a passenger routed backward relative to their own trip is a correctness bug, not a
 *   taste preference, so this is a hard reject here rather than a down-rank).</li>
 *   <li>Min-max normalize each raw component across the surviving set and compute the weighted
 *   composite (§6.5) — normalization is necessarily a batch operation over the whole candidate
 *   set for this one search, not a per-candidate computation in isolation, since "how good is
 *   this candidate" is relative to what else this search found.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ScoringServiceImpl implements ScoringService {

    private final MatchingProperties matchingProperties;

    @Override
    public List<ScoredMatchCandidate> score(PassengerSearchContext context, List<CorridorMatchCandidate> candidates) {
        TripSearchRequest request = context.getRequest();
        GeoPointDto origin = request.getOrigin();
        GeoPointDto destination = request.getDestination();
        double passengerDirectDistance = GeoMath.haversineMeters(
                origin.getLatitude(), origin.getLongitude(), destination.getLatitude(), destination.getLongitude());

        List<RawMetrics> survivors = new ArrayList<>();
        for (CorridorMatchCandidate candidate : candidates) {
            RawMetrics metrics = computeRawMetrics(candidate, origin, destination, passengerDirectDistance, request.getDesiredDepartureTime());
            if (metrics.passengerDetourRatio() <= matchingProperties.getMaxPassengerDetourRatio()) {
                survivors.add(metrics);
            }
        }

        if (survivors.isEmpty()) {
            return List.of();
        }

        Range driverDetourTimeRange = Range.of(survivors, RawMetrics::driverDetourTimeSeconds);
        Range passengerDetourRatioRange = Range.of(survivors, RawMetrics::passengerDetourRatio);
        Range walkFractionRange = Range.of(survivors, RawMetrics::walkFraction);
        Range inverseBenefitRange = Range.of(survivors, m -> 1 - m.benefitRatio());
        Range timeWindowOffsetRange = Range.of(survivors, RawMetrics::timeWindowOffsetMinutes);

        List<ScoredMatchCandidate> results = new ArrayList<>(survivors.size());
        for (RawMetrics metrics : survivors) {
            double compositeScore =
                    matchingProperties.getWeightDriverDetourTime() * driverDetourTimeRange.normalize(metrics.driverDetourTimeSeconds())
                  + matchingProperties.getWeightPassengerDetourRatio() * passengerDetourRatioRange.normalize(metrics.passengerDetourRatio())
                  + matchingProperties.getWeightWalkFraction() * walkFractionRange.normalize(metrics.walkFraction())
                  + matchingProperties.getWeightBenefit() * inverseBenefitRange.normalize(1 - metrics.benefitRatio())
                  + matchingProperties.getWeightTimeWindow() * timeWindowOffsetRange.normalize(metrics.timeWindowOffsetMinutes());

            MatchScore score = new MatchScore(
                    metrics.driverDetourDistanceMeters(),
                    metrics.driverDetourTimeSeconds(),
                    metrics.passengerDetourRatio(),
                    metrics.walkFraction(),
                    metrics.benefitRatio(),
                    metrics.timeWindowOffsetMinutes(),
                    compositeScore);

            results.add(new ScoredMatchCandidate(metrics.candidate(), score));
        }

        return results;
    }

    private RawMetrics computeRawMetrics(
            CorridorMatchCandidate candidate, GeoPointDto origin, GeoPointDto destination,
            double passengerDirectDistance, OffsetDateTime desiredDeparture) {

        Ride ride = candidate.ride();
        NearestPointOnRoute pickup = candidate.pickup();
        NearestPointOnRoute drop = candidate.drop();

        double walkToPickup = GeoMath.haversineMeters(origin.getLatitude(), origin.getLongitude(), pickup.lat(), pickup.lng());
        double walkFromDrop = GeoMath.haversineMeters(drop.lat(), drop.lng(), destination.getLatitude(), destination.getLongitude());
        double totalWalk = walkToPickup + walkFromDrop;
        double passengerDetourRatio = passengerDirectDistance == 0 ? Double.POSITIVE_INFINITY : totalWalk / passengerDirectDistance;

        double ridePortionDistance = drop.cumulativeDistanceMeters() - pickup.cumulativeDistanceMeters();
        double walkFraction = totalWalk / (totalWalk + ridePortionDistance);
        double benefitRatio = passengerDirectDistance == 0 ? 0 : ridePortionDistance / passengerDirectDistance;

        // Pickup/drop are always projected onto the driver's own route (§5.2), so the
        // distance-based portion of the §6.1 insertion cost is provably zero — see
        // MatchingProperties#getStopTimeSeconds() for why.
        double driverDetourDistanceMeters = 0;
        double driverDetourTimeSeconds = 2.0 * matchingProperties.getStopTimeSeconds();

        double timeWindowOffsetMinutes = RouteEtaCalculator.minutesOffsetFromEta(
                desiredDeparture, ride.getDepartureTime(), ride.getRouteGeometry(), pickup.cumulativeDistanceMeters());

        return new RawMetrics(
                candidate, driverDetourDistanceMeters, driverDetourTimeSeconds, passengerDetourRatio,
                walkFraction, benefitRatio, timeWindowOffsetMinutes);
    }

    private record RawMetrics(
            CorridorMatchCandidate candidate,
            double driverDetourDistanceMeters,
            double driverDetourTimeSeconds,
            double passengerDetourRatio,
            double walkFraction,
            double benefitRatio,
            double timeWindowOffsetMinutes) {
    }

    /**
     * Min-max normalization range for one score component across a candidate set. When every
     * candidate has the same value for a component (e.g. {@code driverDetourTimeSeconds}, which
     * is currently a constant per {@link MatchingProperties#getStopTimeSeconds()}'s Javadoc),
     * there's no relative information in that component for this search, so it normalizes to
     * {@code 0} for every candidate rather than dividing by zero.
     */
    private record Range(double min, double max) {

        static Range of(List<RawMetrics> metrics, ToDoubleFunction<RawMetrics> extractor) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (RawMetrics m : metrics) {
                double value = extractor.applyAsDouble(m);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            return new Range(min, max);
        }

        double normalize(double value) {
            if (max - min < 1e-9) {
                return 0;
            }
            return (value - min) / (max - min);
        }
    }
}
