package com.main.server.dto;

// Code with Mosh DTO lesson notes:
// DTO is java object used to package/move data between different parts of my application.
// customized "shipping envelope"

// What the API returns for an airport, deliberately not the Airport entity.
// An entity is tied to the database. Returning it means any column rename
// silently changes the public API contract.
public record AirportDto(
        String icao,
        String iata,
        String name,
        String city,
        Double lat,
        Double lon
) {
}
