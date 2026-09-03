package com.main.server.service;

import com.main.server.dto.OpenSkyFlightDto.Operation;
import com.main.server.dto.OpenSkyFlightDto.Wire;
import com.main.server.entity.Airport;
import com.main.server.entity.Carrier;
import com.main.server.entity.Flight;
import com.main.server.repository.AirportRepository;
import com.main.server.repository.CarrierRepository;
import com.main.server.repository.FlightRepository;
import com.main.server.repository.FlightRepository.RouteProjection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Live ADS-B operations from OpenSky, cached into Postgres.
//
// The one rule that shapes this entire class: OpenSky has NO scheduled-departure
// field, in any endpoint. Delay is by definition actual minus scheduled, so
// nothing sourced here can be called a delay. These are observations of aircraft
// that were seen leaving one airport and arriving at another. Schedules and delay
// ground truth come from BTS, and the two never mix in the same row - OpenSky rows
// are written with source='OPENSKY' and every delay column left null.
@Service
@Slf4j
public class OpenSkyService {

    // ONE day, not the seven the docs describe. The live API enforces a limit its
    // documentation does not mention, and answers a wider window with:
    //     400 "You can only query across 2 partitions (days). Your query will
    //          naturally spill into the 3rd day."
    // OpenSky partitions by UTC calendar day, so a window ending "now" can only be
    // guaranteed to touch two partitions if it is at most 24 hours long. Measured
    // against the real API, not read off the docs.
    private static final int LOOKBACK_DAYS = 1;

    private static final String OPENSKY = "OPENSKY";

    // The token endpoint returns "expires_in" seconds. We refresh a little early
    // so a token cannot expire in flight between our check and OpenSky's.
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient api;
    private final RestClient auth;
    private final String clientId;
    private final String clientSecret;

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final CarrierRepository carrierRepository;

    // This class calling its own @Async method would NOT run it asynchronously.
    // @Async works through a proxy that wraps the bean; a plain `this.method()`
    // call goes straight to the object underneath and skips the wrapper entirely.
    // Injecting the proxy as a field and calling `self.method()` goes through it.
    // @Lazy breaks the chicken-and-egg problem of a bean depending on itself.
    private final OpenSkyService self;

    // volatile: written on one request thread, read on the two opensky-* worker
    // threads. Without it a worker could keep reading a stale cached copy.
    private volatile String accessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public OpenSkyService(RestClient.Builder builder,
                          @Value("${opensky.base-url}") String baseUrl,
                          @Value("${opensky.token-url}") String tokenUrl,
                          @Value("${opensky.client-id}") String clientId,
                          @Value("${opensky.client-secret}") String clientSecret,
                          FlightRepository flightRepository,
                          AirportRepository airportRepository,
                          CarrierRepository carrierRepository,
                          @Lazy OpenSkyService self) {

        this.api = builder.clone().baseUrl(baseUrl)
                .requestFactory(timeouts())
                .build();

        // A separate client: the token comes from auth.opensky-network.org, which
        // is a different host from the API's opensky-network.org.
        this.auth = builder.clone().baseUrl(tokenUrl)
                .requestFactory(timeouts())
                .build();

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.flightRepository = flightRepository;
        this.airportRepository = airportRepository;
        this.carrierRepository = carrierRepository;
        this.self = self;
    }

    // Both clients get the same bounded timeouts. Without these, a hung OpenSky
    // connection would pin an openSkyExecutor thread indefinitely.
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // GET /api/flights/{flightNumber}/operations
    //
    // Returns cached rows when we have them, otherwise spends API credits to fill
    // the cache first and then reads it back. Reading from Postgres in both cases
    // means the two paths return identically shaped data.
    @Transactional
    public List<Operation> recentOperations(String flightNumber, int page, int size) {
        String number = flightNumber.trim().toUpperCase(Locale.ROOT);
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(LOOKBACK_DAYS);

        boolean cached = flightRepository
                .existsByFlightNumberAndSourceAndFlightDateGreaterThanEqual(number, OPENSKY, from);

        if (!cached) {
            fetchAndStore(number);
        }

        return flightRepository
                .findByFlightNumberAndSourceOrderByFlightDateDescIdDesc(
                        number, OPENSKY, PageRequest.of(page, size))
                .stream()
                .map(OpenSkyService::toOperation)
                .toList();
    }

    // The credit-spending path. Only reached on a cache miss.
    private void fetchAndStore(String flightNumber) {

        // OpenSky is queried BY AIRPORT - there is no "give me flight UA1259"
        // endpoint - so we first ask BTS where this flight normally flies.
        List<RouteProjection> routes =
                flightRepository.findRoutesByFrequency(flightNumber, Limit.of(1));

        if (routes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No historical route known for flight " + flightNumber
                            + ", so there is no airport to query");
        }

        String originIata = routes.get(0).getOrigin();
        String destIata = routes.get(0).getDest();

        long end = Instant.now().getEpochSecond();
        long begin = end - Duration.ofDays(LOOKBACK_DAYS).toSeconds();

        // The fan-out. Both calls leave immediately on openSkyExecutor threads and
        // run concurrently; the joins below wait for both. Two calls because either
        // endpoint alone can miss the flight - departure only sees it if OpenSky
        // recognised the origin runway, arrival only if it recognised the
        // destination one.
        CompletableFuture<List<Wire>> departures =
                self.fetchDepartures(icaoOf(originIata), begin, end);
        CompletableFuture<List<Wire>> arrivals =
                self.fetchArrivals(icaoOf(destIata), begin, end);

        List<Wire> found = new ArrayList<>();
        found.addAll(departures.join());
        found.addAll(arrivals.join());

        store(flightNumber, found);
    }

    @Async("openSkyExecutor")
    public CompletableFuture<List<Wire>> fetchDepartures(String airportIcao, long begin, long end) {
        return CompletableFuture.completedFuture(fetch("/flights/departure", airportIcao, begin, end));
    }

    @Async("openSkyExecutor")
    public CompletableFuture<List<Wire>> fetchArrivals(String airportIcao, long begin, long end) {
        return CompletableFuture.completedFuture(fetch("/flights/arrival", airportIcao, begin, end));
    }

    private List<Wire> fetch(String path, String airportIcao, long begin, long end) {
        if (airportIcao == null) {
            return List.of();
        }

        try {
            Wire[] response = withToken(token -> api.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("airport", airportIcao)
                            // Unix SECONDS. Milliseconds here return an empty array
                            // rather than an error, which is the kind of bug that
                            // looks like "the API has no data".
                            .queryParam("begin", begin)
                            .queryParam("end", end)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Wire[].class));

            return response == null ? List.of() : List.of(response);

        } catch (HttpClientErrorException.NotFound e) {
            // OpenSky answers 404, not an empty array, when the window contains no
            // movements at all. That is an ordinary result, not a failure.
            return List.of();
        } catch (RestClientException e) {
            log.warn("OpenSky {} for {} unavailable: {}", path, airportIcao, e.getMessage());
            return List.of();
        }
    }

    // Persist the fetched movements that belong to this flight number.
    private void store(String flightNumber, List<Wire> found) {

        // Everything we already hold for this flight in the window. The flights
        // table has UNIQUE (flight_number, flight_date, origin, dest), so inserting
        // a duplicate would abort the whole batch. Checking first is cheaper than
        // catching the violation, and lets the good rows through.
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(LOOKBACK_DAYS);
        Set<String> seen = new HashSet<>();
        for (Flight existing : flightRepository
                .findByFlightNumberAndFlightDateGreaterThanEqual(flightNumber, from)) {
            seen.add(keyOf(existing.getFlightNumber(), existing.getFlightDate(),
                    iataOf(existing.getOrigin()), iataOf(existing.getDest())));
        }

        // ICAO -> IATA for every airport at once, rather than one lookup per row.
        Map<String, Airport> airportsByIcao = new HashMap<>();
        for (Airport airport : airportRepository.findAll()) {
            airportsByIcao.put(airport.getIcao(), airport);
        }

        // "UAL" -> the UA carrier. This is the reconciliation the whole project
        // turns on: OpenSky says UAL1259, users and BTS say UA1259.
        Map<String, Carrier> carriersByIcao = new HashMap<>();
        for (Carrier carrier : carrierRepository.findAll()) {
            carriersByIcao.put(carrier.getIcao(), carrier);
        }

        // LinkedHashMap keyed by the unique key: the departure and arrival calls
        // both return the same physical flight, so this de-duplicates the two
        // responses against each other while preserving order.
        Map<String, Flight> toSave = new LinkedHashMap<>();

        for (Wire wire : found) {
            Flight flight = toFlight(wire, flightNumber, airportsByIcao, carriersByIcao);
            if (flight == null) {
                continue;
            }

            String key = keyOf(flight.getFlightNumber(), flight.getFlightDate(),
                    iataOf(flight.getOrigin()), iataOf(flight.getDest()));

            if (!seen.contains(key)) {
                toSave.putIfAbsent(key, flight);
            }
        }

        if (!toSave.isEmpty()) {
            flightRepository.saveAll(toSave.values());
        }

        log.info("OpenSky {}: {} movements fetched, {} new rows stored",
                flightNumber, found.size(), toSave.size());
    }

    // One OpenSky movement -> a flights row, or null if it isn't usable.
    private Flight toFlight(Wire wire,
                            String wantedFlightNumber,
                            Map<String, Airport> airportsByIcao,
                            Map<String, Carrier> carriersByIcao) {

        // Callsigns arrive space-padded to 8 characters: "UAL1259 ".
        String callsign = wire.callsign() == null ? null : wire.callsign().trim();
        if (callsign == null || callsign.isEmpty() || wire.firstSeen() == null) {
            return null;
        }

        String iataNumber = toIataFlightNumber(callsign, carriersByIcao);
        if (!wantedFlightNumber.equals(iataNumber)) {
            // A KSFO departure window contains every airline's flights. This is the
            // filter that keeps only the one the user asked about.
            return null;
        }

        Airport origin = airportsByIcao.get(wire.estDepartureAirport());
        Airport dest = airportsByIcao.get(wire.estArrivalAirport());

        // origin and dest are foreign keys into a 609-row table, and both are part
        // of the unique key. A movement to an airport we don't hold cannot be
        // stored coherently, so it is dropped rather than half-written.
        if (origin == null || dest == null) {
            return null;
        }

        Instant departedAt = Instant.ofEpochSecond(wire.firstSeen());

        Flight flight = new Flight();
        flight.setFlightNumber(iataNumber);
        flight.setCallsign(callsign);
        flight.setCarrier(carriersByIcao.get(icaoPrefix(callsign)));
        flight.setOrigin(origin);
        flight.setDest(dest);

        // UTC throughout: airports.tz is null for all 609 rows, so a local-time
        // conversion would be an invented offset.
        flight.setFlightDate(departedAt.atZone(ZoneOffset.UTC).toLocalDate());
        flight.setDepTime(hhmm(departedAt));

        if (wire.lastSeen() != null) {
            flight.setArrTime(hhmm(Instant.ofEpochSecond(wire.lastSeen())));
        }

        // Everything not set above stays null on purpose: crsDepTime, crsArrTime,
        // crsElapsedTime, depDelayMin, arrDelayMin, and the five BTS cause columns.
        // There is no schedule here to compute any of them from.
        flight.setSource(OPENSKY);

        return flight;
    }

    // Token handling.

    // Calls the API with a valid token, and retries exactly once on a 401.
    //
    // A 401 means the cached token was rejected - expired early, or revoked. One
    // retry with a freshly minted token is the whole recovery strategy; if that
    // also fails the credentials are genuinely wrong and the error should surface.
    private <T> T withToken(Function<String, T> call) {
        try {
            return call.apply(accessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("OpenSky rejected the cached token; refreshing once");
            invalidateToken();
            return call.apply(accessToken());
        }
    }

    // synchronized so that when both fan-out threads find an expired token at the
    // same moment, only one of them actually fetches a replacement.
    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }

        // OAuth2 client-credentials: the application authenticates as ITSELF, with
        // no user involved. This is outbound auth to a third party and has nothing
        // to do with user login, which this app deliberately does not have.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse token = auth.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (token == null || token.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenSky did not return an access token");
        }

        accessToken = token.accessToken();
        tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn()).minus(EXPIRY_MARGIN);

        return accessToken;
    }

    private synchronized void invalidateToken() {
        accessToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    // Keycloak's token response. snake_case on the wire, so the two fields are
    // named explicitly rather than by convention.
    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn) {
    }

    // Identifier reconciliation.

    // "UAL1259" -> "UA1259". Returns null when the airline prefix is not one of the
    // 26 carriers we hold, which is most of them - cargo, regional, and foreign
    // operators all share the same airport.
    private String toIataFlightNumber(String callsign, Map<String, Carrier> carriersByIcao) {
        Carrier carrier = carriersByIcao.get(icaoPrefix(callsign));
        if (carrier == null) {
            return null;
        }
        return carrier.getIata() + callsign.substring(3);
    }

    // Airline ICAO codes are always exactly three letters.
    private static String icaoPrefix(String callsign) {
        return callsign.length() <= 3 ? callsign : callsign.substring(0, 3);
    }

    // "SFO" -> "KSFO", because OpenSky's airport parameter takes ICAO only.
    private String icaoOf(String iata) {
        return airportRepository.findByIata(iata).map(Airport::getIcao).orElse(null);
    }

    // Small helpers.

    // BTS-style clock integer: 14:35 UTC becomes 1435, matching how every other
    // time column in the flights table is stored.
    private static Integer hhmm(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).getHour() * 100
                + instant.atZone(ZoneOffset.UTC).getMinute();
    }

    private static String keyOf(String flightNumber, LocalDate date, String origin, String dest) {
        return flightNumber + "|" + date + "|" + origin + "|" + dest;
    }

    private static String iataOf(Airport airport) {
        return airport == null ? null : airport.getIata();
    }

    private static Operation toOperation(Flight f) {
        Instant departedAt = instantOf(f.getFlightDate(), f.getDepTime());
        Instant arrivedAt = instantOf(f.getFlightDate(), f.getArrTime());

        return new Operation(
                f.getFlightNumber(),
                f.getCallsign(),
                f.getFlightDate(),
                iataOf(f.getOrigin()),
                iataOf(f.getDest()),
                departedAt,
                arrivedAt,
                airborneMinutes(departedAt, arrivedAt));
    }

    private static Instant instantOf(LocalDate date, Integer hhmm) {
        if (date == null || hhmm == null) {
            return null;
        }
        return date.atTime(hhmm / 100, hhmm % 100).toInstant(ZoneOffset.UTC);
    }

    private static Integer airborneMinutes(Instant departedAt, Instant arrivedAt) {
        if (departedAt == null || arrivedAt == null) {
            return null;
        }

        // A flight that departs at 23:40 and lands at 00:20 stores both against the
        // same date, so the subtraction comes out negative. Add a day back.
        Duration airborne = Duration.between(departedAt, arrivedAt);
        if (airborne.isNegative()) {
            airborne = airborne.plusDays(1);
        }

        return (int) airborne.toMinutes();
    }
}
