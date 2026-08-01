package com.linkride.backend.controller;

import com.linkride.backend.dto.home.HomeResponse;
import com.linkride.backend.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Home Bootstrap Endpoint — {@code GET /api/home}.
 *
 * <p>Returns every piece of data required to render the Home Screen in a single
 * network request. The authenticated user's UUID is resolved from the Supabase
 * JWT {@code sub} claim via Spring Security's {@link Authentication} principal
 * (populated by {@link com.linkride.backend.filter.JwtAuthFilter}).</p>
 *
 * <h3>Query parameters</h3>
 * <ul>
 *   <li>{@code lat}          (required) — device latitude</li>
 *   <li>{@code lng}          (required) — device longitude</li>
 *   <li>{@code locationName} (optional) — reverse-geocoded place name from the device</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>Requires a valid Supabase JWT. Enforced by
 * {@link com.linkride.backend.config.SecurityConfig} — no additional annotation needed.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home")
    public ResponseEntity<HomeResponse> getHome(
            @RequestParam(value = "lat")                          Double latitude,
            @RequestParam(value = "lng")                          Double longitude,
            @RequestParam(value = "locationName", required = false) String locationName,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        HomeResponse response = homeService.buildHomeResponse(userId, latitude, longitude, locationName);
        return ResponseEntity.ok(response);
    }
}
