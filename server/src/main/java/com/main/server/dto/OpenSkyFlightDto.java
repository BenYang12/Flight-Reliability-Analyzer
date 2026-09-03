package com.main.server.dto;

import java.time.Instant;
import java.time.LocalDate;

// The two shapes involved in an OpenSky lookup
public final class OpenSkyFlightDto {

    private OpenSkyFlightDto() {
    }

    // Exactly what OpenSky's /flights/* endpoints return, one array element.

    public record Wire(
            String icao24,               // the airframe's permanent 24-bit address
            String callsign,             
            Long firstSeen,              // Unix SECONDS, not millis
            Long lastSeen,
            String estDepartureAirport,  // "KSFO" - ICAO form
            String estArrivalAirport
    ) {
    }

    // One recent actual operation, as GET /api/flights/{n}/operations returns it.
   

    public record Operation(
            String flightNumber,    // UA1259 - IATA form, what the user typed
            String callsign,        // UAL1259 - ICAO form, what OpenSky reported
            LocalDate flightDate,
            String origin,          // SFO - IATA form, resolved from KSFO
            String dest,

          
            Instant departedAt,
            Instant arrivedAt,

            
            Integer actualAirborneMinutes
    ) {
    }
}
