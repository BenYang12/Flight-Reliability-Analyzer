package com.main.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Precomputed answer to "how reliable is this carrier, on this route, leaving at this hour?" 
// one row per (carrier, origin, dest, hour).

// Rebuilt nightly by a scheduled job rather than aggregated per request
// a GROUP BY over 200k flights on every page load is slow and
// the underlying data only changes once a day.
@Entity
@Table(name = "route_reliability")
@Getter
@Setter
@NoArgsConstructor
public class RouteReliability {

    @EmbeddedId // flatten everything from RouteReliabilityId into a four column composit id
    private RouteReliabilityId id;

    private Integer totalFlights;

    // On-time: arrival delay <= 15 minutes. That is the FAA's definition!
    private Integer onTimeCount;
    private Double onTimeRate;

    private Double avgArrDelay;

    // 90th percentile. A mean hides tail risk, which is the thing a traveler with a connection actually cares about.
    @Column(name = "p90_arr_delay")
    private Double p90ArrDelay;

    private Double cancelRate;

    private LocalDateTime updatedAt;
}
