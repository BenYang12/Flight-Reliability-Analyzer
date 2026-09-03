package com.main.server.service;

import com.main.server.dto.FlightAnalysisResponse;
import com.main.server.dto.FlightOperationDto;
import com.main.server.dto.SearchResponse;
import com.main.server.entity.Flight;
import com.main.server.mapper.FlightMapper;
import com.main.server.repository.FlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Everything the API can answer from the BTS history we already hold.

// No external calls happen here.
// Future work:
// 1. Checkpoint 6 adds model service on top of this class's output
// 2. Checkpoint 7 adds OpenSky alongside it

@Service
@RequiredArgsConstructor
public class FlightService {

    //`source` column value for historical rows. 
    public static final String BTS = "BTS";
    // How many operations the detail page shows.
    private static final int RECENT_OPERATIONS = 20;
    // THE SAMPLE-SIZE GUARD.
    // Below this many completed flights -> return null instead of a rate. 
    private static final int MIN_SAMPLE = 10;
    private static final int ON_TIME_MINUTES = 15;

    // "UA523" 
    private static final Pattern FLIGHT_NUMBER = Pattern.compile("^[A-Z0-9]{2}\\d{1,4}$");
    private static final Pattern ROUTE = Pattern.compile("^([A-Z]{3})[^A-Z0-9]+([A-Z]{3})$");
    private final FlightRepository flightRepository;


    // GET /api/flights/{flightNumber}
    // @Transactional(readOnly = true) explicitly tells the framework and the underlying persistence provider (like Hibernate) that the executed transaction will only read data
    @Transactional(readOnly = true)
    public FlightAnalysisResponse recentOperations(String flightNumber) {
        String number = normalize(flightNumber);


        List<Flight> operations = flightRepository
                .findByFlightNumberAndSourceOrderByFlightDateDesc(
                        number, BTS, Limit.of(RECENT_OPERATIONS));

        if (operations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No historical operations for flight " + number);
        }
        long total = flightRepository.countByFlightNumberAndSource(number, BTS);
        long completed = flightRepository
                .countByFlightNumberAndSourceAndArrDelayMinNotNull(number, BTS);
        long onTime = flightRepository
                .countByFlightNumberAndSourceAndArrDelayMinLessThanEqual(
                        number, BTS, ON_TIME_MINUTES);

        Flight latest = operations.get(0);

        List<FlightOperationDto> dtos = operations.stream()
                .map(FlightMapper::toDto)
                .toList();

        return new FlightAnalysisResponse(
                number,
                latest.getCarrier() == null ? null : latest.getCarrier().getName(),
                latest.getOrigin() == null ? null : latest.getOrigin().getIata(),
                latest.getDest() == null ? null : latest.getDest().getIata(),
                total,
                completed,
                onTimeRate(onTime, completed),
                dtos);
    }

    // GET /api/search?q=...
    // One search box, two kinds of answer. This method decides which the user
    // meant and confirms we actually hold data for it, so the frontend never
    // navigates to a page that turns out to be empty.
    @Transactional(readOnly = true)
    public SearchResponse resolve(String q) {
        String query = normalize(q);

        // Flight number is checked first, and the two patterns cannot both
        // match: FLIGHT_NUMBER needs digits in positions 3+, ROUTE needs letters
        // there. So the order is for readability, not correctness.
        if (FLIGHT_NUMBER.matcher(query).matches()) {
            return resolveFlightNumber(query);
        }

        Matcher route = ROUTE.matcher(query);
        if (route.matches()) {
            return resolveRoute(route.group(1), route.group(2));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Search for a flight number like UA523 or a route like SFO-JFK");
    }

    private SearchResponse resolveFlightNumber(String number) {
        long count = flightRepository.countByFlightNumberAndSource(number, BTS);
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No flight " + number + " in the historical record");
        }

        Flight latest = flightRepository
                .findByFlightNumberAndSourceOrderByFlightDateDesc(number, BTS, Limit.of(1))
                .get(0);

        return new SearchResponse(
                "FLIGHT",
                number,
                latest.getOrigin() == null ? null : latest.getOrigin().getIata(),
                latest.getDest() == null ? null : latest.getDest().getIata(),
                count);
    }

    private SearchResponse resolveRoute(String origin, String dest) {
        long count = flightRepository.countByOrigin_IataAndDest_IataAndSource(origin, dest, BTS);
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No historical flights from " + origin + " to " + dest);
        }

        return new SearchResponse("ROUTE", null, origin, dest, count);
    }

    private static Double onTimeRate(long onTime, long completed) {
        if (completed < MIN_SAMPLE) {
            return null;
        }
        return (double) onTime / completed;
    }

    private static String normalize(String input) {
        return input.trim().toUpperCase(Locale.ROOT);
    }
}
