package com.main.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One row per airline that BTS reports on.
// Same ICAO/IATA bridge as Airport: OpenSky callsigns start with the ICAO code
// (UAL523), while flight numbers users type use the IATA code (UA523).
@Entity
@Table(name = "carriers")
@Getter
@Setter
@NoArgsConstructor
public class Carrier {

    @Id
    private String icao; // ex. UAL

    @Column(unique = true)
    private String iata; // ex. UA

    private String name; // ex. United Airlines
}
