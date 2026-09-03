package com.main.server.repository;

import com.main.server.entity.Flight;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// Long, because Flight's @Id is a generated BIGSERIAL.
public interface FlightRepository extends JpaRepository<Flight, Long> {

    @Modifying
    @Query("""
            DELETE FROM Flight f
            WHERE f.source = 'BTS'
              AND f.flightDate BETWEEN :start AND :end
            """)
    int deleteBtsRowsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = {"carrier", "origin", "dest"})
    List<Flight> findByFlightNumberAndSourceOrderByFlightDateDesc(
            String flightNumber, String source, Limit limit);
    long countByFlightNumberAndSource(String flightNumber, String source);


    long countByFlightNumberAndSourceAndArrDelayMinNotNull(String flightNumber, String source);

    long countByFlightNumberAndSourceAndArrDelayMinLessThanEqual(
            String flightNumber, String source, int minutes);

  
    long countByOrigin_IataAndDest_IataAndSource(String origin, String dest, String source);
}
