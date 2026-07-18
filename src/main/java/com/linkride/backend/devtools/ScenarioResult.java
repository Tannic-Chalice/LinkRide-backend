package com.linkride.backend.devtools;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Result of {@link DemoScenarioService#runScenario()} -- the step-by-step trace plus the key ids it created. */
@Data
@Builder
public class ScenarioResult {
    private UUID passengerId;
    private UUID driverId;
    private UUID vehicleId;
    private UUID rideId;
    private UUID bookingId;
    private List<ScenarioStep> steps;
}
