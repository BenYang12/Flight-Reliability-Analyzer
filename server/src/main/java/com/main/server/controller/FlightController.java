package com.main.server.controller;

import com.main.server.dto.AirportDto;
import com.main.server.dto.CarrierDto;
import com.main.server.dto.FlightAnalysisResponse;
import com.main.server.dto.SearchResponse;
import com.main.server.service.AirportService;
import com.main.server.service.CarrierService;
import com.main.server.service.FlightService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final AirportService airportService;
    private final CarrierService carrierService;
    private final FlightService flightService;

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
}
