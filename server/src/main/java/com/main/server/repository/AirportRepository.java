package com.main.server.repository;

import com.main.server.entity.Airport;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository is a Spring Data JPA interface that provides common db operations (database access tool)
// Airport Repository is an object that accesses and manages airport rows
// This repository sends queries to the db when I call it
// ex. AirportRepository.findAll() -> "ask the database for all airport rows"
public interface AirportRepository extends JpaRepository<Airport, String> {
}
