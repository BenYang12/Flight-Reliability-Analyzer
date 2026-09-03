package com.main.server.repository;

import com.main.server.entity.Flight;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
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

    // Which route does this flight number usually fly? OpenSky is queried BY
    // AIRPORT, never by flight number, so before we can ask about UA1259 we have
    // to know where UA1259 normally departs from and arrives at.
    //
    // GROUP BY + ORDER BY COUNT(f) DESC = most frequent first. A flight number can
    // be reused on different routes across a season; the busiest one is the one
    // worth spending an API credit on.
    //
    // `f.origin.iata` is an implicit join - JPQL follows the association for us
    // and Hibernate emits the JOIN.
    @Query("""
            SELECT f.origin.iata AS origin, f.dest.iata AS dest
            FROM Flight f
            WHERE f.flightNumber = :flightNumber AND f.source = 'BTS'
            GROUP BY f.origin.iata, f.dest.iata
            ORDER BY COUNT(f) DESC
            """)
    List<RouteProjection> findRoutesByFrequency(@Param("flightNumber") String flightNumber,
                                                Limit limit);

    // A Spring Data "interface projection": we only want two strings, so declaring
    // an interface whose getters match the query's aliases is lighter than loading
    // whole Flight entities. Spring generates the implementation.
    interface RouteProjection {
        String getOrigin();

        String getDest();
    }

    // The cache test. If we already hold OpenSky rows for this flight inside the
    // lookback window, the answer is in Postgres and no API credit is spent.
    boolean existsByFlightNumberAndSourceAndFlightDateGreaterThanEqual(
            String flightNumber, String source, LocalDate from);

    // Paged read of the cached rows. Pageable carries page number + size; the
    // @EntityGraph is here for the same reason as above - without it, reading
    // .getOrigin().getIata() on each row would be one query per distinct airport.
    @EntityGraph(attributePaths = {"carrier", "origin", "dest"})
    List<Flight> findByFlightNumberAndSourceOrderByFlightDateDescIdDesc(
            String flightNumber, String source, Pageable pageable);

    // Everything we already hold for this flight in the window, so newly fetched
    // rows can be checked against the unique key BEFORE insert rather than after a
    // constraint violation kills the whole batch.
    @EntityGraph(attributePaths = {"origin", "dest"})
    List<Flight> findByFlightNumberAndFlightDateGreaterThanEqual(
            String flightNumber, LocalDate from);
}
