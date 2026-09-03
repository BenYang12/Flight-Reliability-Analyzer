package com.main.server.dto;

import java.util.List;

public record RouteReliabilityResponse(
        String origin,
        String dest,
        String carrierIata,
        int totalFlights,
        int onTimeCount,
        double onTimeRate,
        double avgArrDelay,
        List<HourlyReliability> hours
) {
    public record HourlyReliability(
            int depHour,
            int totalFlights,
            int onTimeCount,
            double onTimeRate,
            double avgArrDelay,
            Double p90ArrDelay,
            Double cancelRate
    ) {
    }
}
