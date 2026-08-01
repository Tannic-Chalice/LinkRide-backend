# LinkRide — Backend

LinkRide is a campus carpooling platform. Drivers publish rides they're already making; passengers search for a ride that gets them most of the way to where they're going, along a path the driver was already going to drive. This repository is the Spring Boot backend that powers it: auth, ride creation, route generation, trip matching, booking, and boarding.

## The problem

Most ride-matching products fall into one of two camps:

- **On-demand dispatch** (Uber/Lyft-style) — a live vehicle is routed to you, in real time, at a price set by the platform.
- **Long-haul corridor matching** (BlaBlaCar-style) — a driver posts a fixed point-to-point trip and passengers who happen to be going the same way book a seat.

Neither model fits a college campus well. Trips are short, frequent, and informal — drivers are already going somewhere and would take a rider "if it's on the way," but nobody wants to run a live dispatch system for a five-mile trip to a lecture hall. LinkRide targets that gap: **fixed driver routes, insertion-based passenger matching**. A driver's route never changes shape once created; a passenger is matched against it only if a meaningful stretch of the passenger's own route lies within walking distance of the driver's — not just their two endpoints. Pickup and drop points fall out of where that shared stretch (the "corridor") begins and ends.

The matching design intentionally borrows structure from both worlds: BlaBlaCar's static-route model (because live re-routing isn't worth the complexity for short trips) combined with insertion-cost math lifted from Dial-a-Ride/vehicle-routing literature (because "is this detour worth it for the driver" deserves a real answer, not a guess). The full reasoning — objective function, corridor computation, scoring, complexity analysis, and how it compares to Uber Pool and BlaBlaCar — is written up in [`docs/algo_logic.md`](docs/algo_logic.md).

## What's built

The backend has shipped end-to-end, phase by phase:

| Phase | Capability |
|---|---|
| Auth & Users | Signup/login via Supabase Auth, JWT-authenticated requests, local user profile |
| Vehicles | Vehicle registration, admin verification, active-vehicle selection (drives driver eligibility) |
| Ride Creation | Driver posts pickup, destination, departure time, seats, optional waypoints/repeat schedule |
| Route Generation | Google Directions integration decodes and geometrizes the driver's route (`route/` package) |
| Discovery / Matching | Coarse pre-filtering, spatial corridor computation, multi-criteria scoring, and ranking (`discovery/` package) — see [`docs/algo_logic.md`](docs/algo_logic.md) |
| Booking | Passenger requests a seat, driver accepts/declines, concurrency-safe seat accounting (`booking/` package) |
| Boarding | QR-code and driver-assisted OTP check-in at pickup, boarding-state tracking (`boarding/` package) |
| Platform Hardening | Global error envelope, request correlation IDs, environment profiles, rate limiting, OpenAPI docs, health endpoints — see [`docs/phase-5-platform-hardening.md`](docs/phase-5-platform-hardening.md) |

Full endpoint-by-endpoint contracts (request/response shapes, error codes) live in [`docs/api.md`](docs/api.md) — that file is the source of truth for the API surface and is kept up to date whenever an endpoint changes.

## Architecture at a glance

- **Spring Boot 3.5.15, Java 17, Maven.**
- **Persistence:** PostgreSQL on Supabase (single shared instance across environments — no per-env database), Spring Data JPA/Hibernate. Schema is owned by **Flyway** (`src/main/resources/db/migration/`); Hibernate only validates entities against it (`ddl-auto=validate`).
- **Geospatial:** PostGIS (`geography(Point,4326)` + GiST indexes) via `hibernate-spatial`/JTS for ride/route locations.
- **Auth:** Supabase Auth, not a custom credential store. Requests carry a Supabase-issued JWT (ES256), validated against Supabase's JWKS by a servlet filter; the authenticated principal is the Supabase user UUID.
- **Routing:** Google Directions API for real polylines, with a pluggable `RouteProvider` interface (a no-op stub and a synthetic provider back local/demo runs without hitting the real API or needing an API key).
- **Cross-cutting:** one error envelope + `GlobalExceptionHandler`, request correlation IDs (`X-Request-Id`, propagated through logs via MDC), environment-scoped config (`application-dev.properties` / `application-prod.properties`), per-IP rate limiting on auth endpoints (Bucket4j, in-memory), OpenAPI/Swagger UI, Actuator health/info.

Two package conventions coexist by design — the newer, preferred one groups everything for a feature (controller/service/DTOs) into its own package (`ride/`, `booking/`, `boarding/`, `discovery/`, `vehicle/`); an older split-by-layer style (`dto/`, `service/`, `controller/`, `entity/`) remains for Auth/User/Home/Favorite. New modules should follow the package-per-module style.

## Getting started

### Prerequisites

- JDK 17
- Maven (or use the bundled `./mvnw` / `mvnw.cmd`)
- A Supabase project (Postgres database + Auth) — LinkRide has no local/test database; everything runs against the same cloud Postgres instance
- Docker, if you want to run the Testcontainers-backed concurrency tests

### Configuration

Create a `.env` in the project root (loaded via `spring-dotenv`) with:

```
SUPABASE_DB_PASSWORD=
SUPABASE_JWT_SECRET=
SUPABASE_URL=
SUPABASE_PUBLISHABLE_KEY=
GOOGLE_MAPS_API_KEY=
```

New Spring properties added to `.env` need dotted keys matching `application.properties` placeholders (e.g. `linkride.devtools.enabled=true`), not `SCREAMING_SNAKE_CASE` — relaxed binding only kicks in for properties Spring already knows about.

### Run

```bash
./mvnw spring-boot:run
```

The API is served at `http://localhost:8080`, prefixed `/api/v1`. With the app running:

- Swagger UI: `http://localhost:8080/swagger-ui.html` (non-prod profiles only)
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

### Tests

```bash
./mvnw test
```

Includes Mockito unit tests per service plus Testcontainers-backed concurrency tests (real Postgres, real row locking) for booking and boarding — those require Docker to be running.

### Demo / seed data

A devtools module (off by default, gated by `linkride.devtools.enabled`) can seed a realistic demo dataset — synthetic drivers, passengers, rides, bookings, and boarding activity, using a synthetic route provider so no Google Directions quota is consumed:

```
linkride.devtools.enabled=true
linkride.routing.provider=synthetic
```

Once enabled, `/api/v1/devtools/**` exposes seeding, scenario-running, and reset endpoints for local/demo environments — see `devtools/` in `src/main/java`.

## Project structure

```
src/main/java/com/linkride/backend/
├── controller, dto, service, entity, repository   # Auth, User, Home, Favorite, TripHistory (older layout)
├── vehicle/        # Vehicle registration & verification
├── location/        # Shared geo primitives (GeoPoint, GeoPointDto, lat/lng <-> JTS conversion)
├── route/            # Route generation (Google Directions) + geometry (polyline decode, corridor math)
├── ride/             # Driver ride creation
├── discovery/        # Passenger search: pre-filtering, corridor matching, scoring, ranking
├── booking/          # Booking lifecycle & ride-seat concurrency
├── boarding/         # QR / OTP check-in
├── devtools/         # Demo data seeder & scenario runner (dev-only)
├── config, filter, exception  # Security, JWT auth, correlation IDs, rate limiting, global error handling
└── db/migration/      # Flyway SQL migrations (schema source of truth)
```

## Documentation

- [`docs/api.md`](docs/api.md) — REST API reference (all endpoints, request/response shapes, error codes)
- [`docs/algo_logic.md`](docs/algo_logic.md) — ride-matching algorithm design (corridor matching, scoring, complexity, industry comparison)
- [`docs/phase-5-platform-hardening.md`](docs/phase-5-platform-hardening.md) — cross-cutting platform design (error handling, logging, config, security)
