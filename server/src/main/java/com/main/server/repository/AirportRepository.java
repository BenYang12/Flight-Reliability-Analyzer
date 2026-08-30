package com.main.server.repository;

import com.main.server.entity.Airport;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// JpaRepository is a Spring Data JPA interface that provides common db operations (database access tool)
// Airport Repository is an object that accesses and manages airport rows
// This repository sends queries to the db when I call it
// ex. AirportRepository.findAll() -> "ask the database for all airport rows"
public interface AirportRepository extends JpaRepository<Airport, String> {

    // Typeahead search across the four fields someone might type.
    
    // This is JPQL: it queries the *entity* (Airport, a.iata) and
    // Hibernate translates it to the real table and column names. So it would
    // still work if we renamed a column.
    
    // Prefix match on the codes and city ("SF" should find SFO). 
    @Query("""
            SELECT a FROM Airport a
            WHERE UPPER(a.iata) LIKE UPPER(CONCAT(:q, '%'))
               OR UPPER(a.icao) LIKE UPPER(CONCAT(:q, '%'))
               OR UPPER(a.city) LIKE UPPER(CONCAT(:q, '%'))
               OR UPPER(a.name) LIKE UPPER(CONCAT('%', :q, '%'))
            ORDER BY
                CASE
                    WHEN UPPER(a.iata) = UPPER(:q) THEN 0
                    WHEN UPPER(a.iata) LIKE UPPER(CONCAT(:q, '%')) THEN 1
                    WHEN UPPER(a.city) LIKE UPPER(CONCAT(:q, '%')) THEN 2
                    ELSE 3
                END,
                a.iata
            """)
    List<Airport> search(@Param("q") String q, Limit limit);
}
