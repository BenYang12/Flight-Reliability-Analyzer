package com.main.server.repository;

import com.main.server.entity.RouteReliability;
import com.main.server.entity.RouteReliabilityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteReliabilityRepository extends JpaRepository<RouteReliability, RouteReliabilityId> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO route_reliability (
                carrier_iata, origin, dest, dep_hour,
                total_flights, on_time_count, on_time_rate,
                avg_arr_delay, p90_arr_delay, cancel_rate, updated_at)
            SELECT
                carrier_iata,
                origin,
                dest,
                (crs_dep_time / 100) % 24,
                COUNT(*) FILTER (WHERE arr_delay_min IS NOT NULL),
                COUNT(*) FILTER (WHERE arr_delay_min <= 15),
                COUNT(*) FILTER (WHERE arr_delay_min <= 15)::float8
                    / NULLIF(COUNT(*) FILTER (WHERE arr_delay_min IS NOT NULL), 0),
                AVG(arr_delay_min),
                percentile_cont(0.9) WITHIN GROUP (ORDER BY arr_delay_min),
                COUNT(*) FILTER (WHERE cancelled)::float8 / COUNT(*),
                now()
            FROM flights
            WHERE source = 'BTS' AND crs_dep_time IS NOT NULL
            GROUP BY carrier_iata, origin, dest, (crs_dep_time / 100) % 24
            ON CONFLICT (carrier_iata, origin, dest, dep_hour) DO UPDATE SET
                total_flights = EXCLUDED.total_flights,
                on_time_count = EXCLUDED.on_time_count,
                on_time_rate  = EXCLUDED.on_time_rate,
                avg_arr_delay = EXCLUDED.avg_arr_delay,
                p90_arr_delay = EXCLUDED.p90_arr_delay,
                cancel_rate   = EXCLUDED.cancel_rate,
                updated_at    = EXCLUDED.updated_at
            """)
    int recomputeAll();

    @Query("""
            SELECT r FROM RouteReliability r
            WHERE r.id.origin = :origin
              AND r.id.dest = :dest
              AND r.totalFlights >= :minSample
            ORDER BY r.id.depHour ASC, r.id.carrierIata ASC
            """)
    List<RouteReliability> findForRoute(@Param("origin") String origin,
                                        @Param("dest") String dest,
                                        @Param("minSample") int minSample);

    @Query("""
            SELECT r FROM RouteReliability r
            WHERE r.id.origin = :origin
              AND r.id.dest = :dest
              AND r.id.carrierIata = :carrier
              AND r.totalFlights >= :minSample
            ORDER BY r.id.depHour ASC
            """)
    List<RouteReliability> findForRouteAndCarrier(@Param("origin") String origin,
                                                  @Param("dest") String dest,
                                                  @Param("carrier") String carrier,
                                                  @Param("minSample") int minSample);
}
