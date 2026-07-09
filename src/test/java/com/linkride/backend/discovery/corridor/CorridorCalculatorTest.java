package com.linkride.backend.discovery.corridor;

import com.linkride.backend.route.geometry.CumulativeMetricsCalculator;
import com.linkride.backend.route.geometry.LatLng;
import com.linkride.backend.route.geometry.RouteGeometry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CorridorCalculatorTest {

    /** A straight route along a fixed latitude, evenly spaced, from startLng to endLng. */
    private RouteGeometry straightRoute(double lat, double startLng, double endLng, int numPoints) {
        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < numPoints; i++) {
            double t = (double) i / (numPoints - 1);
            points.add(new LatLng(lat, startLng + t * (endLng - startLng)));
        }
        return CumulativeMetricsCalculator.annotate(points, 1000, 100);
    }

    @Test
    void computeCorridorRuns_identicalRoutes_producesSingleFullOverlapRun() {
        RouteGeometry passenger = straightRoute(12.9716, 77.5900, 77.6100, 20);
        RouteGeometry driver = straightRoute(12.9716, 77.5900, 77.6100, 20);

        List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passenger, driver, 50);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).overlapFraction()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void computeCorridorRuns_tangentialCrossing_producesLowOverlapFraction() {
        // Driver runs east-west; passenger runs north-south, briefly crossing it near the middle.
        RouteGeometry driver = straightRoute(12.9716, 77.5900, 77.6100, 20);

        List<LatLng> passengerPoints = new ArrayList<>();
        int n = 50;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            passengerPoints.add(new LatLng(12.9600 + t * (12.9832 - 12.9600), 77.6000));
        }
        RouteGeometry passenger = CumulativeMetricsCalculator.annotate(passengerPoints, 1000, 100);

        List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passenger, driver, 100);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).overlapFraction()).isLessThan(0.15);
    }

    @Test
    void computeCorridorRuns_divergeThenReconverge_producesTwoDisjointRuns() {
        // Driver dips far north in the middle, close to the passenger's route only near each end.
        RouteGeometry driver = CumulativeMetricsCalculator.annotate(List.of(
                new LatLng(12.9716, 77.5900),
                new LatLng(12.9900, 77.6000),
                new LatLng(12.9716, 77.6100)), 1000, 100);

        RouteGeometry passenger = straightRoute(12.9716, 77.5900, 77.6100, 21);

        List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passenger, driver, 300);

        assertThat(runs).hasSize(2);
    }

    @Test
    void computeCorridorRuns_noPointsWithinTolerance_returnsEmptyList() {
        RouteGeometry passenger = straightRoute(12.9716, 77.5900, 77.6100, 20);
        RouteGeometry driver = straightRoute(20.0000, 78.0000, 78.0200, 20);

        List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passenger, driver, 750);

        assertThat(runs).isEmpty();
    }

    @Test
    void computeCorridorRuns_overlapOnlyAtStart_anchorsRunAtPassengerOrigin() {
        // Driver's route only spans the first fifth of the passenger's route's longitude range.
        RouteGeometry driver = straightRoute(12.9716, 77.5900, 77.5940, 10);
        RouteGeometry passenger = straightRoute(12.9716, 77.5900, 77.6100, 40);

        List<CorridorRun> runs = CorridorCalculator.computeCorridorRuns(passenger, driver, 50);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).entryPoint().cumulativeDistanceMeters()).isCloseTo(0.0, within(1.0));
        assertThat(runs.get(0).overlapFraction()).isLessThan(0.5);
    }
}
