package com.linkride.backend.devtools;

import com.linkride.backend.boarding.BoardingQrResponse;
import com.linkride.backend.boarding.BoardingService;
import com.linkride.backend.booking.BookingCreateRequest;
import com.linkride.backend.booking.BookingResponse;
import com.linkride.backend.booking.BookingService;
import com.linkride.backend.discovery.RideMatchDto;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.TripSearchResponse;
import com.linkride.backend.discovery.TripSearchService;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.ride.RideCreateRequest;
import com.linkride.backend.ride.RideResponse;
import com.linkride.backend.ride.RideService;
import com.linkride.backend.service.UserService;
import com.linkride.backend.vehicle.VehicleRequestDTO;
import com.linkride.backend.vehicle.VehicleResponseDTO;
import com.linkride.backend.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Developer tool -- NOT production code. Runs exactly one fully scripted, narrated
 * passenger+driver journey end to end, logging each step, so the whole application can be
 * demonstrated or screen-recorded without manually clicking through the app. Every step below
 * calls the same real service bean its corresponding controller calls -- see {@link
 * DemoSeedService}'s javadoc for the two narrowly-scoped exceptions (Supabase Auth signup, and
 * vehicle admin-verification) that also apply here.
 *
 * <p>Uses a fixed corridor (Whitefield -> MG Road, full length) so the match is reliably strong
 * and the walkthrough is reproducible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class DemoScenarioService {

    private final UserService userService;
    private final VehicleService vehicleService;
    private final RideService rideService;
    private final BookingService bookingService;
    private final BoardingService boardingService;
    private final TripSearchService tripSearchService;
    private final DevToolsProperties properties;
    private final VehicleRepository vehicleRepository;

    public ScenarioResult runScenario() {
        List<ScenarioStep> steps = new ArrayList<>();
        String runTag = Long.toHexString(System.nanoTime()).substring(0, 6);
        SeedGeoData.Corridor corridor = SeedGeoData.CORRIDORS.get(0); // Whitefield -> MG Road
        List<SeedGeoData.Locality> stops = corridor.stops();
        SeedGeoData.Locality routeStart = stops.get(0);
        SeedGeoData.Locality routeEnd = stops.get(stops.size() - 1);
        OffsetDateTime departureTime = OffsetDateTime.now().plusMinutes(45);

        // 1. Create Passenger
        User passenger = new User();
        passenger.setId(UUID.randomUUID());
        passenger.setFullName("Ananya Sharma");
        passenger.setCollegeEmail("scenario-passenger-%s@%s".formatted(runTag, properties.getSeedEmailDomain()));
        passenger.setPhoneNumber(SeedGeoData.randomPhoneNumber(ThreadLocalRandom.current()));
        passenger.setIsVerified(true);
        User savedPassenger = userService.createTestUser(passenger);
        steps.add(step(1, "Create Passenger",
                "Passenger " + savedPassenger.getFullName() + " (" + savedPassenger.getId() + ") created."));

        // 2. Create Driver (+ register and verify a vehicle -- a driver can't create a ride without one)
        User driver = new User();
        driver.setId(UUID.randomUUID());
        driver.setFullName("Rahul Gowda");
        driver.setCollegeEmail("scenario-driver-%s@%s".formatted(runTag, properties.getSeedEmailDomain()));
        driver.setPhoneNumber(SeedGeoData.randomPhoneNumber(ThreadLocalRandom.current()));
        driver.setIsVerified(true);
        User savedDriver = userService.createTestUser(driver);

        VehicleRequestDTO vehicleRequest = new VehicleRequestDTO();
        vehicleRequest.setNumberPlate("KA01SC" + runTag.toUpperCase());
        vehicleRequest.setCarMake("Maruti Suzuki");
        vehicleRequest.setCarModel("Swift");
        vehicleRequest.setColour("White");
        vehicleRequest.setNoOfSeats(4);
        vehicleRequest.setHasAc(true);
        VehicleResponseDTO vehicleResponse =
                SimulatedAuth.runAs(savedDriver.getId(), () -> vehicleService.registerVehicle(vehicleRequest));

        // No admin-verification service exists yet -- see DemoSeedService's javadoc for why this
        // one direct write is the deliberate exception here too.
        Vehicle vehicle = vehicleRepository.findByOwnerId(savedDriver.getId()).get(0);
        vehicle.setIsVerified(true);
        vehicleRepository.save(vehicle);

        steps.add(step(2, "Create Driver",
                "Driver " + savedDriver.getFullName() + " (" + savedDriver.getId() + ") created with a verified "
                        + vehicleRequest.getCarMake() + " " + vehicleRequest.getCarModel() + "."));

        // 3. Driver creates ride
        RideCreateRequest rideRequest = new RideCreateRequest();
        rideRequest.setPickup(toGeoPointDto(routeStart));
        rideRequest.setDestination(toGeoPointDto(routeEnd));
        rideRequest.setDepartureTime(departureTime);
        rideRequest.setOfferedSeats(3);
        RideResponse ride = rideService.createRide(savedDriver.getId(), rideRequest);
        steps.add(step(3, "Driver creates ride",
                "Ride " + ride.getRideId() + ": " + routeStart.name() + " -> " + routeEnd.name()
                        + ", departing " + departureTime + " (route state: " + ride.getRouteGenerationState() + ")."));

        // 4. Passenger searches rides
        TripSearchRequest searchRequest = new TripSearchRequest();
        searchRequest.setOrigin(toGeoPointDto(routeStart));
        searchRequest.setDestination(toGeoPointDto(routeEnd));
        searchRequest.setDesiredDepartureTime(departureTime);
        searchRequest.setPassengerCount(1);
        TripSearchResponse searchResponse = tripSearchService.search(savedPassenger.getId(), searchRequest);

        if (searchResponse.getMatches().isEmpty()) {
            steps.add(step(4, "Passenger searches rides",
                    "No matches returned -- check linkride.routing.provider=synthetic is set so the "
                            + "ride's route reached READY."));
            return ScenarioResult.builder()
                    .passengerId(savedPassenger.getId())
                    .driverId(savedDriver.getId())
                    .vehicleId(vehicle.getCarId())
                    .rideId(ride.getRideId())
                    .steps(steps)
                    .build();
        }

        RideMatchDto match = searchResponse.getMatches().get(0);
        steps.add(step(4, "Passenger searches rides",
                searchResponse.getMatches().size() + " match(es) found; best match score="
                        + match.getScore() + ", overlap=" + match.getOverlapFraction() + "."));

        // 5. Passenger books ride
        BookingCreateRequest bookingRequest = new BookingCreateRequest();
        bookingRequest.setSeatsRequested(1);
        bookingRequest.setPickup(match.getPickup());
        bookingRequest.setPickupLabel(routeStart.name());
        bookingRequest.setDrop(match.getDrop());
        bookingRequest.setDropLabel(routeEnd.name());
        BookingResponse booking = bookingService.requestBooking(savedPassenger.getId(), match.getRideId(), bookingRequest);
        steps.add(step(5, "Passenger books ride", "Booking " + booking.getBookingId() + " created, status "
                + booking.getStatus() + "."));

        // 6. Driver accepts booking
        booking = bookingService.acceptBooking(savedDriver.getId(), booking.getBookingId());
        steps.add(step(6, "Driver accepts booking", "Booking " + booking.getBookingId() + " now " + booking.getStatus() + "."));

        // 7. Passenger tracks driver
        steps.add(step(7, "Passenger tracks driver",
                "No live-location/tracking service exists in the backend yet (outside Phase 1-4 scope) "
                        + "-- nothing to call here, noted for completeness."));

        // 8. Driver issues QR
        BoardingQrResponse qr = boardingService.issueQr(savedDriver.getId(), booking.getBookingId());
        steps.add(step(8, "Driver issues QR", "QR token issued, expires at " + qr.getExpiresAt() + "."));

        // 9. Passenger checks in
        booking = boardingService.checkInWithToken(savedPassenger.getId(), qr.getToken());
        steps.add(step(9, "Passenger checks in", "Booking " + booking.getBookingId() + " now " + booking.getStatus() + "."));

        // 10. Ride starts
        ride = rideService.startRide(savedDriver.getId(), ride.getRideId());
        steps.add(step(10, "Ride starts", "Ride " + ride.getRideId() + " now " + ride.getStatus() + "."));

        // 11. Ride completes
        ride = rideService.completeRide(savedDriver.getId(), ride.getRideId());
        booking = bookingService.getBooking(savedDriver.getId(), booking.getBookingId());
        steps.add(step(11, "Ride completes", "Ride " + ride.getRideId() + " now " + ride.getStatus()
                + "; booking " + booking.getBookingId() + " now " + booking.getStatus() + "."));

        log.info("[DEMO SCENARIO] completed end-to-end: passenger={}, driver={}, ride={}, booking={}",
                savedPassenger.getId(), savedDriver.getId(), ride.getRideId(), booking.getBookingId());

        return ScenarioResult.builder()
                .passengerId(savedPassenger.getId())
                .driverId(savedDriver.getId())
                .vehicleId(vehicle.getCarId())
                .rideId(ride.getRideId())
                .bookingId(booking.getBookingId())
                .steps(steps)
                .build();
    }

    private ScenarioStep step(int number, String title, String detail) {
        log.info("[DEMO SCENARIO] Step {}: {} -- {}", number, title, detail);
        return ScenarioStep.builder().stepNumber(number).title(title).detail(detail).build();
    }

    private GeoPointDto toGeoPointDto(SeedGeoData.Locality locality) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName(locality.name());
        dto.setAddress(locality.address());
        dto.setLatitude(locality.lat());
        dto.setLongitude(locality.lng());
        return dto;
    }
}
