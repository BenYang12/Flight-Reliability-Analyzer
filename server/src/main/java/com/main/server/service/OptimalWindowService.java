package com.main.server.service;

import com.main.server.dto.OptimalWindowResponse;
import com.main.server.dto.OptimalWindowResponse.RankedHour;
import com.main.server.dto.RouteReliabilityResponse;
import com.main.server.dto.RouteReliabilityResponse.HourlyReliability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Turns hourly table into a booking recommendation.

// This service does no database work of its own. 
// it asks FlightService for Route's hourly reliability, and reshapes the answer.
@Service
@RequiredArgsConstructor
public class OptimalWindowService {
    // dependency injection from COMP301!
    private final FlightService flightService;

    // GET /api/optimal-window?origin=SFO&dest=JFK
    public OptimalWindowResponse forRoute(String origin, String dest) {

        // Throws 404 if the route has no qualifying hours, so everything below can assume a non-empty list.
        RouteReliabilityResponse route = flightService.routeReliability(origin, dest);
        List<HourlyReliability> hours = route.hours();


        List<HourlyReliability> ranked = hours.stream()
                .sorted(Comparator.comparingDouble(HourlyReliability::onTimeRate).reversed()
                        .thenComparingDouble(HourlyReliability::avgArrDelay))
                .toList();

        HourlyReliability best = ranked.get(0);
        HourlyReliability worst = ranked.get(ranked.size() - 1);

        // Grow a contiguous block outward from the best hour.
        int[] window = grow(hours, best.depHour(), route.onTimeRate());

        List<HourlyReliability> inWindow = hours.stream()
                .filter(h -> h.depHour() >= window[0] && h.depHour() <= window[1])
                .toList();

        int windowFlights = inWindow.stream().mapToInt(HourlyReliability::totalFlights).sum();
        int windowOnTime = inWindow.stream().mapToInt(HourlyReliability::onTimeCount).sum();

        double windowDelay = inWindow.stream()
                .mapToDouble(h -> h.avgArrDelay() * h.totalFlights())
                .sum() / windowFlights;

        
        boolean hasContrast = ranked.size() > 1;

        return new OptimalWindowResponse(
                route.origin(),
                route.dest(),
                window[0],
                window[1],
                windowFlights,
                (double) windowOnTime / windowFlights,
                windowDelay,
                route.totalFlights(),
                route.onTimeRate(),
                hasContrast ? worst.depHour() : null,
                hasContrast ? worst.onTimeRate() : null,
                rank(ranked));
    }

    // Helper functions
    // Returns {startHour, endHour}: the widest run of consecutive hours around
    // bestHour where every hour beats the route's own on-time rate.
    private static int[] grow(List<HourlyReliability> hours, int bestHour, double bar) {
        Map<Integer, HourlyReliability> byHour = hours.stream()
                .collect(Collectors.toMap(HourlyReliability::depHour, Function.identity()));

        int start = bestHour;
        while (qualifies(byHour.get(start - 1), bar)) {
            start--;
        }

        int end = bestHour;
        while (qualifies(byHour.get(end + 1), bar)) {
            end++;
        }

        return new int[]{start, end};
    }


    private static boolean qualifies(HourlyReliability hour, double bar) {
        return hour != null && hour.onTimeRate() > bar;
    }

    private static List<RankedHour> rank(List<HourlyReliability> ranked) {
        return java.util.stream.IntStream.range(0, ranked.size())
                .mapToObj(i -> {
                    HourlyReliability h = ranked.get(i);
                    return new RankedHour(
                            i + 1,  // rank is 1-based for display
                            h.depHour(),
                            h.totalFlights(),
                            h.onTimeRate(),
                            h.avgArrDelay());
                })
                .toList();
    }
}
