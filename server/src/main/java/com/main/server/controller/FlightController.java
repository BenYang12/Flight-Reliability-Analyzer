package com.main.server.controller;

import com.main.server.dto.AirportDto;
import com.main.server.dto.AnalyzeServiceDto.Result;
import com.main.server.dto.FlightAnalysisRequest;
import com.main.server.dto.OpenSkyFlightDto.Operation;
import com.main.server.dto.CarrierDto;
import com.main.server.dto.FlightAnalysisResponse;
import com.main.server.dto.OptimalWindowResponse;
import com.main.server.dto.RouteReliabilityResponse;
import com.main.server.dto.SearchResponse;
import com.main.server.service.AirportService;
import com.main.server.service.CarrierService;
import com.main.server.service.FlightAnalysisService;
import com.main.server.service.FlightService;
import com.main.server.service.OpenSkyService;
import com.main.server.service.OptimalWindowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Every route in this API except /api/health lives in this one class.

// @RestController = @Controller + @ResponseBody: Controller's job is only translating HTTP into a method call
// It doesn't know SQL exists

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FlightController {
    
    //fields
    private static final String FLIGHT_NUMBER_REGEX = "^[A-Za-z0-9]{2}\\d{1,4}$";
    private static final String AIRPORT_REGEX = "^[A-Za-z]{3}$";
    private static final String CARRIER_REGEX = "^[A-Za-z0-9]{2}$";
    private final AirportService airportService;
    private final CarrierService carrierService;
    private final FlightService flightService;
    private final OptimalWindowService optimalWindowService;
    private final FlightAnalysisService flightAnalysisService;
    private final OpenSkyService openSkyService;

    // GET /api/airports?q=SF
    // @RequestParam is required by default, so a missing q returns 400.
    @GetMapping("/airports")
    public List<AirportDto> searchAirports(@RequestParam String q) {
        return airportService.search(q);
    }

    // GET /api/carriers
    // The 26 airlines BTS reports on. 
    @GetMapping("/carriers")
    public List<CarrierDto> carriers() {
        return carrierService.findAll();
    }

    // GET /api/search?q=UA523  or  ?q=SFO-JFK
    // Resolves whichever the user typed and reports which it was.
    @GetMapping("/search")
    public SearchResponse search(@RequestParam @NotBlank String q) {
        return flightService.resolve(q);
    }

    // GET /api/flights/UA523
    @GetMapping("/flights/{flightNumber}")
    public FlightAnalysisResponse flight(
            @PathVariable
            @Pattern(regexp = FLIGHT_NUMBER_REGEX,
                     message = "must look like a flight number, e.g. UA523")
            String flightNumber) {

        return flightService.recentOperations(flightNumber);
    }

    @GetMapping("/routes/{origin}/{dest}")
    public RouteReliabilityResponse route(
            @PathVariable
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String origin,

            @PathVariable
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String dest) {

        return flightService.routeReliability(origin, dest);
    }

    @GetMapping("/reliability")
    public RouteReliabilityResponse reliability(
            @RequestParam
            @Pattern(regexp = CARRIER_REGEX, message = "must be a 2-character airline code")
            String carrier,

            @RequestParam
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String origin,

            @RequestParam
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String dest) {

        return flightService.hourlyReliability(carrier, origin, dest);
    }

    // GET /api/optimal-window?origin=SFO&dest=JFK
    // The same hourly data as /api/routes, ranked into a "book this window" answer. 
    @GetMapping("/optimal-window")
    public OptimalWindowResponse optimalWindow(
            @RequestParam
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String origin,

            @RequestParam
            @Pattern(regexp = AIRPORT_REGEX, message = "must be a 3-letter airport code")
            String dest) {

        return optimalWindowService.forRoute(origin, dest);
    }

    // GET /api/flights/UA1259/operations?page=0&size=20
    //
    // RECENT ACTUAL OPERATIONS, not delays. These come from OpenSky's ADS-B
    // telemetry, which carries no scheduled time anywhere in the API, so nothing
    // in this response can be a delay. The historical, schedule-based numbers live
    // at /api/flights/{flightNumber}.
    @GetMapping("/flights/{flightNumber}/operations")
    public List<Operation> operations(
            @PathVariable
            @Pattern(regexp = FLIGHT_NUMBER_REGEX,
                     message = "must look like a flight number, e.g. UA523")
            String flightNumber,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            // Capped: this endpoint is backed by a rate-limited upstream, and an
            // unbounded size would let one request ask for every cached row.
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "must be at least 1")
            @Max(value = 100, message = "must be 100 or less")
            int size) {

        return openSkyService.recentOperations(flightNumber, page, size);
    }

    @PostMapping("/analyze")
    public Result analyze(@Valid @RequestBody FlightAnalysisRequest request) {
        return flightAnalysisService.analyze(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The analysis service is unavailable"));
    }
}
