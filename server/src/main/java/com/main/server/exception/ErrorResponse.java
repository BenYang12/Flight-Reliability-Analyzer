package com.main.server.exception;

import java.time.Instant;

// single JSON shape every failure in this API comes back as.
// record is class designed to act as immutable data carrier
public record ErrorResponse(
        int status,     
        String message,   
        String path,       // which URL failed, e.g. /api/flights/UA523
        Instant timestamp  
) {
}
