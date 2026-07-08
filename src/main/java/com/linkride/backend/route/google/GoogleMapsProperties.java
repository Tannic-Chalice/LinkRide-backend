package com.linkride.backend.route.google;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "linkride.google-maps")
public class GoogleMapsProperties {

    private String apiKey;
    private String directionsBaseUrl = "https://maps.googleapis.com/maps/api/directions/json";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private int maxRetries = 2;
    private Duration retryBackoff = Duration.ofMillis(200);
}
