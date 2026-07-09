package com.linkride.backend.discovery;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Everything a passenger-search pipeline stage might need about who is searching and what
 * they're searching for: passenger identity, the raw request, and the passenger's own computed
 * route. Assembled once by {@link TripSearchServiceImpl} after validation and route computation,
 * then passed as a single object to every subsequent stage — candidate generation now, corridor
 * computation / constraints / scoring / ranking later — instead of each stage's method signature
 * growing its own parameter list. Fields like passenger preferences, blocked users, or
 * organization scoping land here as this grows, not as new method parameters.
 */
@Data
@Builder
public class PassengerSearchContext {

    private UUID passengerId;
    private TripSearchRequest request;
    private PassengerRoute passengerRoute;
}
