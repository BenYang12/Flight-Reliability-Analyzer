package com.main.server.dto;

// What /api/carriers returns for one airline.

// Both codes are exposed on purpose. The frontend shows `iata` (UA, the form on
// a boarding pass) but OpenSky callsigns are built from `icao` (UAL523), so the
// mapping between them is genuinely part of this API's contract.
public record CarrierDto(
        String icao,  // UAL
        String iata,  // UA
        String name   // United Air Lines Inc.
) {
}
