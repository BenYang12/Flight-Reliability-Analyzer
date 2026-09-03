package com.main.server.dto;

import jakarta.validation.constraints.NotNull;

public record FlightAnalysisRequest(
        @NotNull(message = "is required") Integer crsDepTime,
        @NotNull(message = "is required") Integer crsElapsedTime,
        @NotNull(message = "is required") Integer depDelayMin,
        @NotNull(message = "is required") Integer arrDelayMin,
        @NotNull(message = "is required") Integer taxiOut,
        @NotNull(message = "is required") Integer taxiIn,
        @NotNull(message = "is required") Integer distance,
        @NotNull(message = "is required") Integer dayOfWeek,
        @NotNull(message = "is required") Integer month,

        Integer carrierDelay,
        Integer weatherDelay,
        Integer nasDelay,
        Integer lateAircraftDelay
) {
}
