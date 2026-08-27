package com.main.server.repository;

import com.main.server.entity.Flight;
import org.springframework.data.jpa.repository.JpaRepository;

// Long, because Flight's @Id is a generated BIGSERIAL.
public interface FlightRepository extends JpaRepository<Flight, Long> {
}
