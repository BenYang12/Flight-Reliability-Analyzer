package com.main.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Everything else lives on the one fat FlightController 
 // GET /api/health -> {"status":"UP"}


@RestController
@RequestMapping("/api")
public class HealthController {

    // Map.of(...) builds a small immutable map, which Jackson serializes into that JSON object. 
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
