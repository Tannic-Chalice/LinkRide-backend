package com.linkride.backend.devtools;

import com.linkride.backend.boarding.BoardingQrResponse;
import com.linkride.backend.boarding.BoardingService;
import com.linkride.backend.boarding.BoardingTokenRepository;
import com.linkride.backend.boarding.BoardingVerificationRepository;
import com.linkride.backend.booking.Booking;
import com.linkride.backend.booking.BookingCreateRequest;
import com.linkride.backend.booking.BookingRepository;
import com.linkride.backend.booking.BookingResponse;
import com.linkride.backend.booking.BookingService;
import com.linkride.backend.discovery.RideMatchDto;
import com.linkride.backend.discovery.TripSearchRequest;
import com.linkride.backend.discovery.TripSearchResponse;
import com.linkride.backend.discovery.TripSearchService;
import com.linkride.backend.entity.User;
import com.linkride.backend.entity.Vehicle;
import com.linkride.backend.location.GeoPointDto;
import com.linkride.backend.repository.UserRepository;
import com.linkride.backend.repository.VehicleRepository;
import com.linkride.backend.ride.Ride;
import com.linkride.backend.ride.RideCreateRequest;
import com.linkride.backend.ride.RideRepository;
import com.linkride.backend.ride.RideResponse;
import com.linkride.backend.ride.RideService;
import com.linkride.backend.route.RouteGenerationState;
import com.linkride.backend.service.UserService;
import com.linkride.backend.vehicle.VehicleRequestDTO;
import com.linkride.backend.vehicle.VehicleService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Developer tool -- NOT production code. Bootstraps a realistic LinkRide environment (~50 drivers,
 * ~150 passengers, spread-out rides, bookings, boarding activity) from an empty database by
 * calling the same real service beans the API controllers call, so every piece of business logic
 * (seat capacity, corridor scoring, booking lifecycle, boarding verification) runs exactly as it
 * would for a real user. Only reachable when {@code linkride.devtools.enabled=true}.
 *
 * <p>Two deliberate, narrowly-scoped exceptions to "always go through a service":</p>
 * <ul>
 *   <li>Seeded users are created via {@code UserService.createTestUser} instead of {@code
 *       registerUser} -- the latter calls the real Supabase Auth signup API, which is unworkable
 *       for ~200 accounts (rate limits, real email requirements, external cost) and is
 *       third-party identity infrastructure, not app business logic.</li>
 *   <li>Vehicles are marked {@code isVerified=true} via a direct repository write after {@code
 *       VehicleService.registerVehicle} -- there is no admin-verification service anywhere in the
 *       app yet (a real gap), and {@code RideService.createRide} hard-requires a verified vehicle.</li>
 * </ul>
 *
 * <p>Every other step -- ride creation, trip search, booking, acceptance/rejection, cancellation,
 * QR/OTP boarding, ride start/complete -- calls the real {@code RideService}/{@code
 * BookingService}/{@code BoardingService}/{@code TripSearchService}, including the mixed booking
 * statuses (PENDING/ACCEPTED/CHECKED_IN/REJECTED/CANCELLED/EXPIRED/NO_SHOW/COMPLETED): those fall
 * out naturally from the real cascades in {@code RideService.startRide/completeRide} and {@code
 * BookingServiceImpl}, not from anything hardcoded here.</p>
 *
 * <p>Requires {@code linkride.routing.provider=synthetic} (see {@link
 * com.linkride.backend.route.SyntheticRouteProvider}) -- candidate generation only considers
 * routes that reached {@code READY}, which neither the real Google provider (too slow/costly for
 * bulk seeding) nor the no-op provider (never reaches READY) can give it for free.</p>
 *
 * <p>Every seeded user's {@code collegeEmail} ends in {@code @<seedEmailDomain>} (default {@code
 * @linkride.seed}) -- that marker is what {@link #reset()} uses to find and remove exactly the
 * rows this tool created, and nothing else.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "linkride.devtools", name = "enabled", havingValue = "true")
public class DemoSeedService {

    private final UserService userService;
    private final VehicleService vehicleService;
    private final RideService rideService;
    private final BookingService bookingService;
    private final BoardingService boardingService;
    private final TripSearchService tripSearchService;
    private final CapturingOtpNotificationService otpCapture;
    private final DevToolsProperties properties;

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final BoardingTokenRepository boardingTokenRepository;
    private final BoardingVerificationRepository boardingVerificationRepository;
    private final EntityManager entityManager;

    private record SeededDriver(UUID id, SeedGeoData.Corridor corridor, int seatCapacity) {
    }

    private record SeededRide(UUID rideId, UUID driverId, SeedGeoData.Corridor corridor,
                               int startIdx, int endIdx, OffsetDateTime departureTime) {
    }

    // ─── Bootstrap ──────────────────────────────────────────────────────────────

    public BootstrapSummary bootstrap() {
        long startedAt = System.currentTimeMillis();
        String runTag = Long.toHexString(System.nanoTime()).substring(0, 6);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        log.info("[DEMO SEED] Starting bootstrap, runTag={}", runTag);

        List<SeededDriver> drivers = createDrivers(runTag, random);
        log.info("[DEMO SEED] {} drivers created with verified vehicles", drivers.size());

        List<SeededRide> rides = createRides(drivers, random);
        log.info("[DEMO SEED] {} rides created", rides.size());

        List<UUID> rideIds = rides.stream().map(SeededRide::rideId).toList();
        Map<UUID, SeededRide> rideById = rides.stream().collect(Collectors.toMap(SeededRide::rideId, r -> r));

        List<UUID> passengerIds = new ArrayList<>();
        List<BookingResponse> bookings = createPassengersAndBook(runTag, rides, random, passengerIds);
        log.info("[DEMO SEED] {} passengers created, {} bookings created from real search results",
                passengerIds.size(), bookings.size());

        Map<UUID, List<BookingResponse>> bookingsByRide = bookings.stream()
                .collect(Collectors.groupingBy(BookingResponse::getRideId));

        List<BookingResponse> accepted = decideBookings(rideById, bookingsByRide, random);
        log.info("[DEMO SEED] driver decisions made on {} bookings ({} accepted)", bookings.size(), accepted.size());

        List<BookingResponse> boardable = cancelSome(accepted, rideById, random);

        int[] boardingCounts = boardRides(boardable, rideById, random); // [qr, otp]
        log.info("[DEMO SEED] boarding: {} via QR, {} via OTP", boardingCounts[0], boardingCounts[1]);

        int[] lifecycleCounts = runLifecycle(rideById, bookingsByRide, random); // [started, completed, cancelled]
        log.info("[DEMO SEED] lifecycle: {} started, {} completed, {} ride-cancelled",
                lifecycleCounts[0], lifecycleCounts[1], lifecycleCounts[2]);

        // Spring's Open-Session-In-View keeps one Hibernate session alive for this entire HTTP
        // request. Every entity touched above (rides, bookings, ...) is still sitting in that
        // session's first-level cache -- including ones the bulk @Modifying updates inside
        // runLifecycle's cascades (expireAllPendingBookingsForRide etc.) just changed in the
        // database *without* going through that cache. Left alone, the query below would silently
        // hand back those stale, pre-lifecycle copies instead of each booking's real current
        // status. Clearing the persistence context forces a genuinely fresh read.
        entityManager.clear();
        Map<String, Integer> finalCounts = tallyBookingStatuses(rideIds);

        return BootstrapSummary.builder()
                .runTag(runTag)
                .driversCreated(drivers.size())
                .passengersCreated(passengerIds.size())
                .vehiclesCreated(drivers.size())
                .ridesCreated(rides.size())
                .searchesRun(passengerIds.size())
                .bookingsCreated(bookings.size())
                .bookingsByFinalStatus(finalCounts)
                .qrCheckIns(boardingCounts[0])
                .otpCheckIns(boardingCounts[1])
                .ridesStarted(lifecycleCounts[0])
                .ridesCompleted(lifecycleCounts[1])
                .ridesCancelled(lifecycleCounts[2])
                .elapsedMillis(System.currentTimeMillis() - startedAt)
                .build();
    }

    // ─── Part 1: drivers + vehicles ────────────────────────────────────────────

    private List<SeededDriver> createDrivers(String runTag, ThreadLocalRandom random) {
        List<SeededDriver> drivers = new ArrayList<>();

        for (int i = 1; i <= properties.getDriverCount(); i++) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setFullName(SeedGeoData.randomFullName(random));
            user.setCollegeEmail("driver%02d-%s@%s".formatted(i, runTag, properties.getSeedEmailDomain()));
            user.setPhoneNumber(SeedGeoData.randomPhoneNumber(random));
            user.setIsVerified(true);
            User saved = userService.createTestUser(user);

            SeedGeoData.Corridor corridor = SeedGeoData.randomCorridor(random);
            int seatCapacity = 3 + random.nextInt(4); // 3..6

            VehicleRequestDTO vehicleRequest = new VehicleRequestDTO();
            vehicleRequest.setNumberPlate(randomPlate(random));
            String[] makeModel = SeedGeoData.CAR_MAKES_MODELS[random.nextInt(SeedGeoData.CAR_MAKES_MODELS.length)]
                    .split("\\|");
            vehicleRequest.setCarMake(makeModel[0]);
            vehicleRequest.setCarModel(makeModel[1]);
            vehicleRequest.setColour(SeedGeoData.COLOURS[random.nextInt(SeedGeoData.COLOURS.length)]);
            vehicleRequest.setNoOfSeats(seatCapacity);
            vehicleRequest.setHasAc(random.nextBoolean());

            registerVehicleWithRetry(saved.getId(), vehicleRequest, random);

            // No admin-verification service exists anywhere in the app yet -- the one deliberate
            // direct-write exception here, standing in for that missing feature (see class javadoc).
            Vehicle vehicle = vehicleRepository.findByOwnerId(saved.getId()).get(0);
            vehicle.setIsVerified(true);
            vehicleRepository.save(vehicle);

            drivers.add(new SeededDriver(saved.getId(), corridor, seatCapacity));
        }

        return drivers;
    }

    private void registerVehicleWithRetry(UUID driverId, VehicleRequestDTO request, ThreadLocalRandom random) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                SimulatedAuth.runAs(driverId, () -> vehicleService.registerVehicle(request));
                return;
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT && attempt < 4) {
                    request.setNumberPlate(randomPlate(random)); // number-plate collision, retry
                } else {
                    throw e;
                }
            }
        }
    }

    private String randomPlate(ThreadLocalRandom random) {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        return "KA%02d%c%c%04d".formatted(
                1 + random.nextInt(60),
                letters.charAt(random.nextInt(letters.length())),
                letters.charAt(random.nextInt(letters.length())),
                random.nextInt(10000));
    }

    // ─── Part 2: rides ──────────────────────────────────────────────────────────

    private List<SeededRide> createRides(List<SeededDriver> drivers, ThreadLocalRandom random) {
        List<SeededRide> rides = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (SeededDriver driver : drivers) {
            int rideCount = properties.getMinRidesPerDriver()
                    + random.nextInt(properties.getMaxRidesPerDriver() - properties.getMinRidesPerDriver() + 1);

            for (int r = 0; r < rideCount; r++) {
                List<SeedGeoData.Locality> stops = driver.corridor().stops();
                int startIdx = random.nextInt(stops.size() - 1);
                int endIdx = startIdx + 1 + random.nextInt(stops.size() - startIdx - 1);

                GeoPointDto pickup = toGeoPointDto(stops.get(startIdx), random);
                GeoPointDto destination = toGeoPointDto(stops.get(endIdx), random);
                List<GeoPointDto> waypoints = new ArrayList<>();
                for (int idx = startIdx + 1; idx < endIdx; idx++) {
                    waypoints.add(toGeoPointDto(stops.get(idx), random));
                }

                OffsetDateTime departureTime =
                        now.plusMinutes(30L + random.nextInt(properties.getDepartureSpreadHours() * 60));

                RideCreateRequest request = new RideCreateRequest();
                request.setPickup(pickup);
                request.setDestination(destination);
                request.setWaypoints(waypoints);
                request.setDepartureTime(departureTime);
                request.setOfferedSeats(1 + random.nextInt(driver.seatCapacity()));

                RideResponse response = rideService.createRide(driver.id(), request);

                if (response.getRouteGenerationState() != RouteGenerationState.READY) {
                    log.warn("[DEMO SEED] ride {} did not reach READY route state ({}) -- set "
                                    + "linkride.routing.provider=synthetic for seeding, or candidate "
                                    + "generation will silently skip this ride.",
                            response.getRideId(), response.getRouteGenerationState());
                }

                rides.add(new SeededRide(response.getRideId(), driver.id(), driver.corridor(), startIdx, endIdx,
                        departureTime));
            }
        }

        return rides;
    }

    // ─── Part 3a: passengers + real search-driven bookings ────────────────────

    private List<BookingResponse> createPassengersAndBook(
            String runTag, List<SeededRide> rides, ThreadLocalRandom random, List<UUID> passengerIdsOut) {

        List<BookingResponse> bookings = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (int i = 1; i <= properties.getPassengerCount(); i++) {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setFullName(SeedGeoData.randomFullName(random));
            user.setCollegeEmail("passenger%03d-%s@%s".formatted(i, runTag, properties.getSeedEmailDomain()));
            user.setPhoneNumber(SeedGeoData.randomPhoneNumber(random));
            user.setIsVerified(true);
            User saved = userService.createTestUser(user);
            passengerIdsOut.add(saved.getId());

            GeoPointDto origin;
            GeoPointDto destination;
            OffsetDateTime desiredDeparture;

            double intent = random.nextDouble();
            if (intent < 0.55 && !rides.isEmpty()) {
                // Aligned with an existing ride's own corridor segment -> tends toward strong,
                // high-overlap matches.
                SeededRide ref = rides.get(random.nextInt(rides.size()));
                List<SeedGeoData.Locality> stops = ref.corridor().stops();
                int lo = Math.max(0, ref.startIdx() - random.nextInt(2));
                int hi = Math.min(stops.size() - 1, ref.endIdx() + random.nextInt(2));
                if (lo >= hi) {
                    lo = ref.startIdx();
                    hi = ref.endIdx();
                }
                origin = toGeoPointDto(stops.get(lo), random);
                destination = toGeoPointDto(stops.get(hi), random);
                desiredDeparture = clampFuture(ref.departureTime().plusMinutes(random.nextInt(41) - 20), now);
            } else if (intent < 0.85) {
                // A random corridor segment, wider departure offset -> partial/weak overlap, or
                // outright validation failures (pickup time tolerance, min overlap fraction).
                SeedGeoData.Corridor corridor = SeedGeoData.randomCorridor(random);
                List<SeedGeoData.Locality> stops = corridor.stops();
                int lo = random.nextInt(stops.size() - 1);
                int hi = lo + 1 + random.nextInt(stops.size() - lo - 1);
                origin = toGeoPointDto(stops.get(lo), random);
                destination = toGeoPointDto(stops.get(hi), random);
                OffsetDateTime base = rides.isEmpty() ? now.plusHours(2)
                        : rides.get(random.nextInt(rides.size())).departureTime();
                desiredDeparture = clampFuture(base.plusMinutes(random.nextInt(121) - 60), now);
            } else {
                // Deliberately unrelated cross-town trip -> exercises rejected/empty-result search.
                SeedGeoData.Corridor c1 = SeedGeoData.randomCorridor(random);
                SeedGeoData.Corridor c2 = differentCorridor(c1, random);
                origin = toGeoPointDto(c1.stops().get(0), random);
                destination = toGeoPointDto(c2.stops().get(c2.stops().size() - 1), random);
                desiredDeparture = now.plusMinutes(30L + random.nextInt(properties.getDepartureSpreadHours() * 60));
            }

            TripSearchRequest searchRequest = new TripSearchRequest();
            searchRequest.setOrigin(origin);
            searchRequest.setDestination(destination);
            searchRequest.setDesiredDepartureTime(desiredDeparture);
            searchRequest.setPassengerCount(1);

            TripSearchResponse searchResponse = tripSearchService.search(saved.getId(), searchRequest);

            if (!searchResponse.getMatches().isEmpty()) {
                RideMatchDto best = searchResponse.getMatches().get(0); // ranked, best first
                BookingCreateRequest bookingRequest = new BookingCreateRequest();
                bookingRequest.setSeatsRequested(1);
                bookingRequest.setPickup(best.getPickup());
                bookingRequest.setPickupLabel(origin.getName());
                bookingRequest.setDrop(best.getDrop());
                bookingRequest.setDropLabel(destination.getName());

                try {
                    bookings.add(bookingService.requestBooking(saved.getId(), best.getRideId(), bookingRequest));
                } catch (ResponseStatusException e) {
                    log.debug("[DEMO SEED] passenger {} could not book ride {}: {}",
                            saved.getId(), best.getRideId(), e.getReason());
                }
            }
        }

        return bookings;
    }

    private SeedGeoData.Corridor differentCorridor(SeedGeoData.Corridor exclude, ThreadLocalRandom random) {
        SeedGeoData.Corridor candidate;
        do {
            candidate = SeedGeoData.randomCorridor(random);
        } while (candidate == exclude && SeedGeoData.CORRIDORS.size() > 1);
        return candidate;
    }

    private OffsetDateTime clampFuture(OffsetDateTime candidate, OffsetDateTime now) {
        OffsetDateTime floor = now.plusMinutes(10);
        return candidate.isBefore(floor) ? floor : candidate;
    }

    private GeoPointDto toGeoPointDto(SeedGeoData.Locality locality, ThreadLocalRandom random) {
        GeoPointDto dto = new GeoPointDto();
        dto.setName(locality.name());
        dto.setAddress(locality.address());
        double jitter = 0.0025; // ~250m, so nobody lands on the exact same point
        dto.setLatitude(locality.lat() + (random.nextDouble() * 2 - 1) * jitter);
        dto.setLongitude(locality.lng() + (random.nextDouble() * 2 - 1) * jitter);
        return dto;
    }

    // ─── Part 3b: driver decisions + cancellations ─────────────────────────────

    private List<BookingResponse> decideBookings(
            Map<UUID, SeededRide> rideById, Map<UUID, List<BookingResponse>> bookingsByRide, ThreadLocalRandom random) {

        List<BookingResponse> accepted = new ArrayList<>();

        for (Map.Entry<UUID, List<BookingResponse>> entry : bookingsByRide.entrySet()) {
            UUID rideId = entry.getKey();
            UUID driverId = rideById.get(rideId).driverId();

            for (BookingResponse booking : entry.getValue()) {
                double r = random.nextDouble();
                if (r < 0.15) {
                    continue; // left PENDING -- driver hasn't decided yet, a real, common state
                }

                try {
                    if (r < 0.15 + properties.getAcceptRate() * 0.85) {
                        accepted.add(bookingService.acceptBooking(driverId, booking.getBookingId()));
                    } else {
                        bookingService.rejectBooking(driverId, booking.getBookingId());
                    }
                } catch (ResponseStatusException e) {
                    // e.g. the ride's seats filled up from an earlier accept on the same ride --
                    // exactly the capacity guard Part 3 asks the seeder to respect, not bypass.
                    log.debug("[DEMO SEED] decision on booking {} failed: {}", booking.getBookingId(), e.getReason());
                }
            }
        }

        return accepted;
    }

    private List<BookingResponse> cancelSome(
            List<BookingResponse> accepted, Map<UUID, SeededRide> rideById, ThreadLocalRandom random) {

        List<BookingResponse> stillBoardable = new ArrayList<>();

        for (BookingResponse booking : accepted) {
            if (random.nextDouble() < properties.getCancellationRate()) {
                UUID driverId = rideById.get(booking.getRideId()).driverId();
                UUID requesterId = random.nextBoolean() ? booking.getPassengerId() : driverId;
                try {
                    bookingService.cancelBooking(requesterId, booking.getBookingId());
                    continue;
                } catch (ResponseStatusException e) {
                    log.debug("[DEMO SEED] cancellation of booking {} failed: {}",
                            booking.getBookingId(), e.getReason());
                }
            }
            stillBoardable.add(booking);
        }

        return stillBoardable;
    }

    // ─── Part 4: boarding ───────────────────────────────────────────────────────

    private int[] boardRides(List<BookingResponse> boardable, Map<UUID, SeededRide> rideById, ThreadLocalRandom random) {
        int qrCount = 0;
        int otpCount = 0;

        for (BookingResponse booking : boardable) {
            UUID driverId = rideById.get(booking.getRideId()).driverId();
            UUID passengerId = booking.getPassengerId();
            double r = random.nextDouble();

            try {
                if (r < properties.getQrCheckInRate()) {
                    BoardingQrResponse qr = boardingService.issueQr(driverId, booking.getBookingId());
                    boardingService.checkInWithToken(passengerId, qr.getToken());
                    qrCount++;
                } else if (r < properties.getQrCheckInRate() + properties.getOtpCheckInRate()) {
                    boardingService.generateOtp(driverId, booking.getBookingId());
                    String otp = otpCapture.getLastOtp(passengerId);
                    boardingService.verifyOtp(driverId, booking.getBookingId(), otp);
                    otpCount++;
                }
                // else: left ACCEPTED, never boarded -> becomes NO_SHOW once the ride starts.
            } catch (ResponseStatusException | IllegalStateException e) {
                log.debug("[DEMO SEED] boarding for booking {} failed: {}", booking.getBookingId(), e.getMessage());
            }
        }

        return new int[] {qrCount, otpCount};
    }

    // ─── Ride lifecycle ─────────────────────────────────────────────────────────

    private int[] runLifecycle(
            Map<UUID, SeededRide> rideById, Map<UUID, List<BookingResponse>> bookingsByRide, ThreadLocalRandom random) {

        int started = 0;
        int completed = 0;
        int cancelled = 0;

        for (SeededRide ride : rideById.values()) {
            if (!bookingsByRide.containsKey(ride.rideId())) {
                continue; // no interaction on this ride -- leave it purely SCHEDULED
            }

            double r = random.nextDouble();
            try {
                if (r < 0.05) {
                    rideService.cancelRide(ride.driverId(), ride.rideId());
                    cancelled++;
                } else if (r < 0.05 + properties.getRideCompletionRate()) {
                    rideService.startRide(ride.driverId(), ride.rideId());
                    started++;
                    rideService.completeRide(ride.driverId(), ride.rideId());
                    completed++;
                } else if (r < 0.20 + properties.getRideCompletionRate()) {
                    rideService.startRide(ride.driverId(), ride.rideId());
                    started++;
                }
                // else: left SCHEDULED, bookings stay exactly as boarding left them.
            } catch (ResponseStatusException e) {
                log.debug("[DEMO SEED] lifecycle transition on ride {} failed: {}", ride.rideId(), e.getReason());
            }
        }

        return new int[] {started, completed, cancelled};
    }

    private Map<String, Integer> tallyBookingStatuses(List<UUID> rideIds) {
        if (rideIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (Booking booking : bookingRepository.findByRide_RideIdIn(rideIds)) {
            counts.merge(booking.getStatus().name(), 1, Integer::sum);
        }
        return counts;
    }

    // ─── Reset ──────────────────────────────────────────────────────────────────

    /**
     * Deletes only rows reachable from a seeded user (matched by {@code
     * collegeEmail} ending in {@code @<seedEmailDomain>}) -- never a blanket truncate, safe to
     * call at any time, and safe against real (non-seeded) data in the same database.
     */
    @Transactional
    public ResetSummary reset() {
        List<UUID> userIds = userRepository
                .findByCollegeEmailEndingWith("@" + properties.getSeedEmailDomain())
                .stream().map(User::getId).toList();

        if (userIds.isEmpty()) {
            return ResetSummary.builder().build();
        }

        List<UUID> rideIds = rideRepository.findByDriver_IdIn(userIds).stream().map(Ride::getRideId).toList();
        List<UUID> bookingIds = rideIds.isEmpty() ? List.of()
                : bookingRepository.findByRide_RideIdIn(rideIds).stream().map(Booking::getBookingId).toList();

        int verificationsDeleted = bookingIds.isEmpty() ? 0
                : boardingVerificationRepository.deleteByBooking_BookingIdIn(bookingIds);
        int tokensDeleted = bookingIds.isEmpty() ? 0
                : boardingTokenRepository.deleteByBooking_BookingIdIn(bookingIds);
        int bookingsDeleted = rideIds.isEmpty() ? 0 : bookingRepository.deleteByRide_RideIdIn(rideIds);
        int ridesDeleted = rideRepository.deleteByDriver_IdIn(userIds);
        int vehiclesDeleted = vehicleRepository.deleteByOwnerIdIn(userIds);
        userRepository.deleteAllById(userIds);

        return ResetSummary.builder()
                .boardingVerificationsDeleted(verificationsDeleted)
                .boardingTokensDeleted(tokensDeleted)
                .bookingsDeleted(bookingsDeleted)
                .ridesDeleted(ridesDeleted)
                .vehiclesDeleted(vehiclesDeleted)
                .usersDeleted(userIds.size())
                .build();
    }

    /** Cheap point-in-time counts of currently-seeded data, for {@code GET /api/devtools/status}. */
    public Map<String, Object> status() {
        List<UUID> userIds = userRepository
                .findByCollegeEmailEndingWith("@" + properties.getSeedEmailDomain())
                .stream().map(User::getId).toList();
        List<UUID> rideIds = userIds.isEmpty() ? List.of()
                : rideRepository.findByDriver_IdIn(userIds).stream().map(Ride::getRideId).toList();
        int bookingCount = rideIds.isEmpty() ? 0 : bookingRepository.findByRide_RideIdIn(rideIds).size();

        Map<String, Object> status = new HashMap<>();
        status.put("seededUsers", userIds.size());
        status.put("seededRides", rideIds.size());
        status.put("seededBookings", bookingCount);
        return status;
    }
}
