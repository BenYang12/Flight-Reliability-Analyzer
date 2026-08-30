package com.main.server.controller;

import com.main.server.dto.AirportDto;
import com.main.server.service.AirportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
// @Controller lives in presentation layer (Web/API), while @Service bean lives in business logic layer. 
// Every route in this API lives in this one class

// @RestController = @Controller + @ResponseBody: whatever a method returns is
// serialized straight to JSON by Jackson, instead of being treated as the name
// of an HTML view to render.
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FlightController {

    private final AirportService airportService;

    // GET /api/airports?q=SF
    // @RequestParam is required by default, so a missing q returns 400.
    @GetMapping("/airports")
    public List<AirportDto> searchAirports(@RequestParam String q) {
        return airportService.search(q);
    }
}
