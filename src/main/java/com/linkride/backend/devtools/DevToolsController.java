package com.linkride.backend.devtools;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Developer tool -- NOT production code. Only registered in the Spring context at all when {@code
 * linkride.devtools.enabled=true} (default {@code false}) -- the whole {@code /api/devtools/**}
 * route surface simply doesn't exist otherwise, in production or in a plain local run. See {@link
 * DemoSeedService} and {@link DemoScenarioService} for what each endpoint does and why; see {@code
 * backend/docs/api.md} for the endpoint contracts and a runbook.
 */
@RestController
@RequestMapping("/api/v1/devtools")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class DevToolsController {

    private final DemoSeedService demoSeedService;
    private final DemoScenarioService demoScenarioService;

    /** Bulk-bootstraps ~50 drivers, ~150 passengers, rides, bookings, and boarding activity. Safe to call repeatedly -- each run gets a fresh, uniquely-tagged batch. */
    @PostMapping("/seed/bootstrap")
    public ResponseEntity<BootstrapSummary> bootstrap() {
        return ResponseEntity.ok(demoSeedService.bootstrap());
    }

    /** Runs one fully narrated passenger+driver walkthrough of the whole application, step by step. */
    @PostMapping("/seed/scenario")
    public ResponseEntity<ScenarioResult> scenario() {
        return ResponseEntity.ok(demoScenarioService.runScenario());
    }

    /** Deletes every row this tool has ever created (matched by the seed email marker), and nothing else. */
    @PostMapping("/seed/reset")
    public ResponseEntity<ResetSummary> reset() {
        return ResponseEntity.ok(demoSeedService.reset());
    }

    /** Point-in-time counts of currently-seeded data. */
    @GetMapping("/seed/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(demoSeedService.status());
    }

    /**
     * The last 50 notifications across every recipient, newest first — e.g. POST a booking,
     * accept it, then call this to see the resulting {@code BOOKING_ACCEPTED} notification without
     * a mobile client or real FCM credentials (Phase 6 — see backend/docs/phase-6-notifications.md).
     */
    @GetMapping("/push-log")
    public ResponseEntity<List<PushLogEntry>> pushLog() {
        return ResponseEntity.ok(demoSeedService.pushLog());
    }
}
