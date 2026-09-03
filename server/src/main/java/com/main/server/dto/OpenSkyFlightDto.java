package com.main.server.dto;

import java.time.Instant;
import java.time.LocalDate;

// The two shapes involved in an OpenSky lookup, kept in one file because they
// describe the same thing at two stages: what the network gave us, and what our
// API hands out.
//
// Same pattern as AnalyzeServiceDto: a holder class you never instantiate, with
// the records nested inside it.
public final class OpenSkyFlightDto {

    private OpenSkyFlightDto() {
    }

    // Exactly what OpenSky's /flights/* endpoints return, one array element.
    //
    // OpenSky already speaks camelCase, so unlike the Flask DTO this needs no
    // naming annotation. The fields we ignore (the four *AirportHorizDistance /
    // *CandidatesCount diagnostics) are simply absent - Spring Boot configures
    // Jackson to skip unknown properties rather than fail on them.
    //
    // "est" is OpenSky's own prefix: the airports are ESTIMATED by matching the
    // aircraft's first and last ADS-B position against known runways. Either can
    // be null when the aircraft was not seen near a recognised airport.
    public record Wire(
            String icao24,               // the airframe's permanent 24-bit address
            String callsign,             // "UAL1259 " - ICAO form, trailing-padded
            Long firstSeen,              // Unix SECONDS, not millis
            Long lastSeen,
            String estDepartureAirport,  // "KSFO" - ICAO form
            String estArrivalAirport
    ) {
    }

    // One recent actual operation, as GET /api/flights/{n}/operations returns it.
    //
    // IMPORTANT: there is deliberately no delay field anywhere in this record, and
    // there never can be. Delay is actual MINUS SCHEDULED, and OpenSky has no
    // scheduled time in any endpoint. Everything here is an observation.
    public record Operation(
            String flightNumber,    // UA1259 - IATA form, what the user typed
            String callsign,        // UAL1259 - ICAO form, what OpenSky reported
            LocalDate flightDate,
            String origin,          // SFO - IATA form, resolved from KSFO
            String dest,

            // Wheels-up and wheels-down, as observed. UTC: airports.tz is null for
            // every row in the table, so converting to local time would mean
            // inventing an offset.
            Instant departedAt,
            Instant arrivedAt,

            // lastSeen - firstSeen. The time the aircraft was actually airborne,
            // which is NOT the same as BTS's gate-to-gate crs_elapsed_time and must
            // never be compared against it. Null when the arrival was not observed.
            Integer actualAirborneMinutes
    ) {
    }
}
