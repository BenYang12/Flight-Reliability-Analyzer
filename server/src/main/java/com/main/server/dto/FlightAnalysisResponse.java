package com.main.server.dto;

import java.util.List;

// Everything the flight-detail page needs for one flight number.

public record FlightAnalysisResponse(
        String flightNumber,
        String carrierName,
        String origin,        
        String dest,
        long totalOperations,
        long completedOperations,   // excludes cancellations, which have no arrival time
        Double onTimeRate,

        // The most recent operations, newest first.
        List<FlightOperationDto> operations
) {
}
