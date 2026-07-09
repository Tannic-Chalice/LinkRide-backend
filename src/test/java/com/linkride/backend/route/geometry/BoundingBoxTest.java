package com.linkride.backend.route.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundingBoxTest {

    @Test
    void of_computesEnvelopeAcrossAllPoints() {
        BoundingBox box = BoundingBox.of(List.of(
                new LatLng(12.90, 77.50),
                new LatLng(13.10, 77.70),
                new LatLng(12.95, 77.60)));

        assertThat(box.minLat()).isEqualTo(12.90);
        assertThat(box.maxLat()).isEqualTo(13.10);
        assertThat(box.minLng()).isEqualTo(77.50);
        assertThat(box.maxLng()).isEqualTo(77.70);
    }

    @Test
    void overlaps_intersectingBoxes_returnsTrue() {
        BoundingBox a = BoundingBox.of(List.of(new LatLng(12.90, 77.50), new LatLng(13.00, 77.60)));
        BoundingBox b = BoundingBox.of(List.of(new LatLng(12.95, 77.55), new LatLng(13.05, 77.65)));

        assertThat(a.overlaps(b, 0)).isTrue();
    }

    @Test
    void overlaps_farApartBoxes_returnsFalseWithoutPadding() {
        BoundingBox a = BoundingBox.of(List.of(new LatLng(12.90, 77.50), new LatLng(13.00, 77.60)));
        BoundingBox b = BoundingBox.of(List.of(new LatLng(20.00, 80.00), new LatLng(20.10, 80.10)));

        assertThat(a.overlaps(b, 0)).isFalse();
    }

    @Test
    void overlaps_adjacentBoxes_becomeOverlappingWithEnoughPadding() {
        BoundingBox a = BoundingBox.of(List.of(new LatLng(12.90, 77.50), new LatLng(13.00, 77.50)));
        BoundingBox b = BoundingBox.of(List.of(new LatLng(12.90, 77.60), new LatLng(13.00, 77.60)));

        assertThat(a.overlaps(b, 100)).isFalse();
        assertThat(a.overlaps(b, 50_000)).isTrue();
    }
}
