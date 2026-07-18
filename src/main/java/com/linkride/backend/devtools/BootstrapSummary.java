package com.linkride.backend.devtools;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Result of {@link DemoSeedService#bootstrap()} — counts of everything the seeder created. */
@Data
@Builder
public class BootstrapSummary {
    private String runTag;
    private int driversCreated;
    private int passengersCreated;
    private int vehiclesCreated;
    private int ridesCreated;
    private int searchesRun;
    private int bookingsCreated;
    private Map<String, Integer> bookingsByFinalStatus;
    private int qrCheckIns;
    private int otpCheckIns;
    private int ridesStarted;
    private int ridesCompleted;
    private int ridesCancelled;
    private long elapsedMillis;
}
