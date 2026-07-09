package com.linkride.backend.discovery;

import com.linkride.backend.route.RouteProvider;
import com.linkride.backend.route.RouteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Delegates to the existing {@link RouteProvider} exactly as {@code RideService} does for
 * driver ride creation — same provider, same retry/failure handling, just for a passenger's
 * origin/destination instead of a driver's pickup/destination. A passenger search never carries
 * waypoints, so the waypoint list passed to the provider is always empty. Mapping the raw
 * {@link RouteResult} into the discovery module's {@link PassengerRoute} shape is delegated to
 * {@link PassengerRouteMapper}, not done inline here.
 */
@Service
@RequiredArgsConstructor
public class PassengerRouteServiceImpl implements PassengerRouteService {

    private final RouteProvider routeProvider;
    private final PassengerRouteMapper passengerRouteMapper;

    @Override
    public PassengerRoute computeRoute(TripSearchRequest request) {
        RouteResult routeResult = routeProvider.computeRoute(request.getOrigin(), List.of(), request.getDestination());
        return passengerRouteMapper.toPassengerRoute(routeResult);
    }
}
