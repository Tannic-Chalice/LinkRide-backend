package com.linkride.backend.discovery;

import com.linkride.backend.location.GeoPointDto;
import org.springframework.stereotype.Component;

/**
 * Business-rule validation for {@link TripSearchRequest} that {@code jakarta.validation}
 * annotations on the DTO can't express (cross-field checks). Kept separate from
 * {@link TripSearchServiceImpl} so the service reads as pure orchestration and new rules
 * (e.g. a departure-window sanity check once candidate generation lands in 2B.3) have a
 * single obvious place to go.
 */
@Component
public class TripSearchRequestValidator {

    public void validate(TripSearchRequest request) {
        if (isSameLocation(request.getOrigin(), request.getDestination())) {
            throw new IllegalArgumentException("Origin and destination must not be the same location");
        }
    }

    private boolean isSameLocation(GeoPointDto a, GeoPointDto b) {
        return a.getLatitude().equals(b.getLatitude()) && a.getLongitude().equals(b.getLongitude());
    }
}
