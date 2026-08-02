package com.linkride.backend.tracking;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §7.1 step 1 — coordinate bounds are the one check this pipeline delegates to declarative
 * Bean Validation rather than {@link GpsFixValidator}.
 */
class LocationUpdateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validRequest_hasNoViolations() {
        LocationUpdateRequest request = request(12.9716, 77.5946, Instant.now());

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void missingRecordedAt_isRejected() {
        LocationUpdateRequest request = request(12.9716, 77.5946, null);

        Set<ConstraintViolation<LocationUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("recordedAt"));
    }

    @Test
    void latitudeAboveNinety_isRejected() {
        LocationUpdateRequest request = request(90.1, 77.5946, Instant.now());

        Set<ConstraintViolation<LocationUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("latitude"));
    }

    @Test
    void latitudeBelowNegativeNinety_isRejected() {
        LocationUpdateRequest request = request(-90.1, 77.5946, Instant.now());

        assertThat(validator.validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("latitude"));
    }

    @Test
    void longitudeOutOfBounds_isRejected() {
        LocationUpdateRequest request = request(12.9716, 180.1, Instant.now());

        assertThat(validator.validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("longitude"));
    }

    @Test
    void boundaryCoordinates_areAccepted() {
        assertThat(validator.validate(request(90.0, 180.0, Instant.now()))).isEmpty();
        assertThat(validator.validate(request(-90.0, -180.0, Instant.now()))).isEmpty();
    }

    private LocationUpdateRequest request(Double latitude, Double longitude, Instant recordedAt) {
        LocationUpdateRequest request = new LocationUpdateRequest();
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setRecordedAt(recordedAt);
        return request;
    }
}
