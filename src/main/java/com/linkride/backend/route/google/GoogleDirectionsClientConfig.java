package com.linkride.backend.route.google;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to call Google Directions, wiring in base URL and
 * connect/read timeouts from {@link GoogleMapsProperties}. Kept separate from
 * {@link GoogleDirectionsClientImpl} so tests can bind a mock server to a plain
 * {@code RestClient.Builder} without this timeout/base-URL wiring getting in the way.
 */
@Configuration
public class GoogleDirectionsClientConfig {

    @Bean
    public RestClient googleDirectionsRestClient(RestClient.Builder builder, GoogleMapsProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        return builder
                .baseUrl(properties.getDirectionsBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
