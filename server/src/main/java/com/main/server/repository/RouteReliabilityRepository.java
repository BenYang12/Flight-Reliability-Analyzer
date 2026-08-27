package com.main.server.repository;

import com.main.server.entity.RouteReliability;
import com.main.server.entity.RouteReliabilityId;
import org.springframework.data.jpa.repository.JpaRepository;

// The id type is the composite key class itself, so findById() takes a
// fully-populated RouteReliabilityId.
public interface RouteReliabilityRepository extends JpaRepository<RouteReliability, RouteReliabilityId> {
}
