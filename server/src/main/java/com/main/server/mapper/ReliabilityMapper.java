package com.main.server.mapper;

import com.main.server.dto.RouteReliabilityResponse;
import com.main.server.dto.RouteReliabilityResponse.HourlyReliability;
import com.main.server.entity.RouteReliability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReliabilityMapper {

    private ReliabilityMapper() {
    }

    public static RouteReliabilityResponse toResponse(String origin,
                                                      String dest,
                                                      String carrierIata,
                                                      List<RouteReliability> rows) {
        List<HourlyReliability> hours = carrierIata == null
                ? combineCarriers(rows)
                : rows.stream().map(ReliabilityMapper::toHour).toList();

        int totalFlights = hours.stream().mapToInt(HourlyReliability::totalFlights).sum();
        int onTimeCount = hours.stream().mapToInt(HourlyReliability::onTimeCount).sum();
        double weightedDelay = hours.stream()
                .mapToDouble(h -> h.avgArrDelay() * h.totalFlights())
                .sum();

        return new RouteReliabilityResponse(
                origin,
                dest,
                carrierIata,
                totalFlights,
                onTimeCount,
                (double) onTimeCount / totalFlights,
                weightedDelay / totalFlights,
                hours);
    }

    private static HourlyReliability toHour(RouteReliability r) {
        return new HourlyReliability(
                r.getId().getDepHour(),
                r.getTotalFlights(),
                r.getOnTimeCount(),
                r.getOnTimeRate(),
                r.getAvgArrDelay(),
                r.getP90ArrDelay(),
                r.getCancelRate());
    }

    private static List<HourlyReliability> combineCarriers(List<RouteReliability> rows) {
        Map<Integer, List<RouteReliability>> byHour = new LinkedHashMap<>();
        for (RouteReliability r : rows) {
            byHour.computeIfAbsent(r.getId().getDepHour(), h -> new ArrayList<>()).add(r);
        }

        return byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<RouteReliability> group = entry.getValue();
                    int totalFlights = group.stream().mapToInt(RouteReliability::getTotalFlights).sum();
                    int onTimeCount = group.stream().mapToInt(RouteReliability::getOnTimeCount).sum();
                    double weightedDelay = group.stream()
                            .mapToDouble(r -> r.getAvgArrDelay() * r.getTotalFlights())
                            .sum();

                    return new HourlyReliability(
                            entry.getKey(),
                            totalFlights,
                            onTimeCount,
                            (double) onTimeCount / totalFlights,
                            weightedDelay / totalFlights,
                            null,
                            null);
                })
                .sorted(Comparator.comparingInt(HourlyReliability::depHour))
                .toList();
    }
}
