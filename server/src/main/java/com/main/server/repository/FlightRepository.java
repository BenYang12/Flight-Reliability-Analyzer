package com.main.server.repository;

import com.main.server.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

// Long, because Flight's @Id is a generated BIGSERIAL.
public interface FlightRepository extends JpaRepository<Flight, Long> {

    // Clears the date window an incremental import is about to rewrite, so
    // re-importing a day is safe instead of colliding with uk_flights_operation.
    //
    // Scoped to source='BTS' on purpose: OpenSky rows land in this same table
    // in Phase 5 and must survive a BTS refresh.
    //
    // @Modifying tells Spring Data this query changes rows rather than reading
    // them — without it, Hibernate refuses to run a DELETE here.
    @Modifying
    @Query("""
            DELETE FROM Flight f
            WHERE f.source = 'BTS'
              AND f.flightDate BETWEEN :start AND :end
            """)
    int deleteBtsRowsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
