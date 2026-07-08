package com.linkride.backend.route.google;

import com.linkride.backend.location.GeoPointDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleDirectionsClientTest {

    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/directions/json";

    private GeoPointDto point(double lat, double lng) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName("Test");
        dto.setAddress("Test address");
        dto.setLatitude(lat);
        dto.setLongitude(lng);
        return dto;
    }

    private record Fixture(GoogleDirectionsClientImpl client, MockRestServiceServer server) {
    }

    private Fixture buildFixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey("test-api-key");
        properties.setDirectionsBaseUrl(BASE_URL);

        return new Fixture(new GoogleDirectionsClientImpl(restClient, properties), server);
    }

    @Test
    void sendsDrivingModeAndCoordinatesWithoutWaypoints() {
        Fixture fixture = buildFixture();

        fixture.server().expect(requestTo(startsWith(BASE_URL)))
                .andExpect(method(GET))
                .andExpect(queryParam("origin", "1.0,2.0"))
                .andExpect(queryParam("destination", "3.0,4.0"))
                .andExpect(queryParam("mode", "driving"))
                .andExpect(queryParam("key", "test-api-key"))
                .andRespond(withSuccess("{ \"status\": \"OK\", \"routes\": [] }", MediaType.APPLICATION_JSON));

        GoogleDirectionsResponse response = fixture.client().fetchRoute(point(1.0, 2.0), List.of(), point(3.0, 4.0));

        assertThat(response.getStatus()).isEqualTo("OK");
        fixture.server().verify();
    }

    @Test
    void includesPipeSeparatedWaypoints() {
        Fixture fixture = buildFixture();

        fixture.server().expect(requestTo(startsWith(BASE_URL)))
                .andExpect(method(GET))
                .andExpect(queryParam("waypoints", "1.5,2.5%7C1.8,2.8"))
                .andRespond(withSuccess("{ \"status\": \"OK\", \"routes\": [] }", MediaType.APPLICATION_JSON));

        GoogleDirectionsResponse response = fixture.client().fetchRoute(
                point(1.0, 2.0), List.of(point(1.5, 2.5), point(1.8, 2.8)), point(3.0, 4.0));

        assertThat(response.getStatus()).isEqualTo("OK");
        fixture.server().verify();
    }

    @Test
    void omitsWaypointsParamWhenNoneGiven() {
        Fixture fixture = buildFixture();

        fixture.server().expect(requestTo(startsWith(BASE_URL)))
                .andRespond(withSuccess("{ \"status\": \"ZERO_RESULTS\" }", MediaType.APPLICATION_JSON));

        GoogleDirectionsResponse response = fixture.client().fetchRoute(point(1.0, 2.0), null, point(3.0, 4.0));

        assertThat(response.getStatus()).isEqualTo("ZERO_RESULTS");
        fixture.server().verify();
    }
}
