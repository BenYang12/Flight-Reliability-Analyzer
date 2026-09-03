package com.main.server.repository;

import com.main.server.entity.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarrierRepository extends JpaRepository<Carrier, String> {

    // 26 rows, sorted for a dropdown. Sorting in SQL rather than in Java because
    // Postgres is already reading the rows and can order them for free.
    List<Carrier> findAllByOrderByNameAsc();
}
