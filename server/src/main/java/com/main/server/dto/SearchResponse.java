package com.main.server.dto;

// The answer to "what did the user actually type?"

public record SearchResponse(
        String type,          // "FLIGHT" or "ROUTE"
        String flightNumber,  // set for FLIGHT, null for ROUTE
        String origin,        // set for both: a flight number resolves to its usual route
        String dest,

        // How many historical operations back this match. The frontend uses it
        // to decide whether a result is worth showing a statistic for at all.
        long operationCount
) {
}
