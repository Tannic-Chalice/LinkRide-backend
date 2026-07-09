package com.linkride.backend.discovery;

import com.linkride.backend.location.GeoPointDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripSearchRequestValidatorTest {

    private final TripSearchRequestValidator validator = new TripSearchRequestValidator();

    private TripSearchRequest validRequest() {
        TripSearchRequest request = new TripSearchRequest();
        request.setOrigin(geoPoint(12.9716, 77.5946));
        request.setDestination(geoPoint(13.1986, 77.7066));
        request.setDesiredDepartureTime(OffsetDateTime.now().plusHours(2));
        request.setPassengerCount(1);
        return request;
    }

    private GeoPointDto geoPoint(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("Test Location");
        dto.setAddress("123 Test Street");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    @Test
    void validate_originDiffersFromDestination_doesNotThrow() {
        assertThatCode(() -> validator.validate(validRequest())).doesNotThrowAnyException();
    }

    @Test
    void validate_originEqualsDestination_throwsValidationError() {
        TripSearchRequest request = validRequest();
        request.setDestination(geoPoint(12.9716, 77.5946)); // same as origin

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be the same location");
    }
}
