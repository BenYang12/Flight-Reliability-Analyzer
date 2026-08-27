package com.main.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Entity is lightweight Java class that maps to a database table
// One row per US airport, seeded from OurAirports.
// Keyed by ICAO because that is what OpenSky speaks; IATA is what humans speak
// This table is the bridge between the two.
@Entity
@Table(name = "airports")
@Getter // Lombok
@Setter
@NoArgsConstructor // JPA requires a no-arg constructor to build objects from rows.

public class Airport {

    @Id
    private String icao; // ICAO used for planning, safety, navigation. What OpenSky uses 

    // Unique, so `flights.origin` can point at this column as a foreign key.
    @Column(unique = true)
    private String iata; 

    private String name;
    private String city;
    private String country;
    private Double lat;
    private Double lon;

    private String tz; // IANA zone
}
