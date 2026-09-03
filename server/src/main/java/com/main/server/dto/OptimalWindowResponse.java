package com.main.server.dto;

import java.util.List;

// What GET /api/optimal-window returns: a booking recommendation for one route,
// plus the ranking that justifies it.


public record OptimalWindowResponse(
        String origin,
        String dest,
        int startHour, //optimal departure window
        int endHour,

        // The window's own numbers, aggregated across the hours it covers.
        int windowFlights,
        double windowOnTimeRate,
        double windowAvgArrDelay,

        // The whole route, for comparison. Without this the window's rate is a
        // number with nothing to beat: 78% is excellent on one route and poor
        // on another.
        int routeFlights,
        double routeOnTimeRate,

        // The hour to avoid. Nullable only in the degenerate case where the
        // route has exactly one qualifying hour, so best and worst coincide.
        Integer worstHour,
        Double worstHourOnTimeRate,

        // Every qualifying hour, best first. The evidence behind the window.
        // actual field insise OptimalWindowResponse
        List<RankedHour> hours 
) {
    public record RankedHour( // nested record type
            int rank,          // 1 = best on-time rate
            int depHour,
            int totalFlights,
            double onTimeRate,
            double avgArrDelay
    ) {
    }
}
