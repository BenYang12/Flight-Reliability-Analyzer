package com.main.server.entity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

// One operation of one flight on one day.
// Two kinds of row live here:
//   BTS     —> historical, has scheduled times and thus real delay values.
//   OPENSKY —>recent actual movements. No scheduled time exists in that API,
//             so its delay columns stay null. Never present these as delays.
@Entity
@Table(
        name = "flights",
        // I will require combination of flight number, date, origin, and destination to be unique
        // So I can protect statistics from duplicate imports
        uniqueConstraints = @UniqueConstraint(
                name = "uk_flights_operation",
                columnNames = {"flight_number", "flight_date", "origin", "dest"}
        ),
        // index let ms make flight-number and route lookups faster.
        indexes = {
                @Index(name = "idx_flights_number", columnList = "flight_number"),
                @Index(name = "idx_flights_route", columnList = "origin,dest")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Flight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String flightNumber; // UA523  (IATA form, what users type)
    private String callsign;     // UAL523 (ICAO form, what OpenSky reports)

    // Relations
    // ManyToOne means many records of one type can point to same record of another type (many flights belong to one carrier)
    @ManyToOne(fetch = FetchType.LAZY) // declares that many Flight objects may reference carrier
    @JoinColumn(name = "carrier_iata", referencedColumnName = "iata")
    private Carrier carrier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin", referencedColumnName = "iata")
    private Airport origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest", referencedColumnName = "iata")
    private Airport dest;

    // Times 
    // BTS encodes clock times as int rs_` = scheduled.
    private LocalDate flightDate;
    private Integer crsDepTime;
    private Integer depTime;
    private Integer crsArrTime;
    private Integer arrTime;


    // Gate to Gate time, straight from BTS.
    // Stored b/c the Flask service needs it to compute delay_ratio (arr_delay / scheduled block time)
    private Integer crsElapsedTime;

    // Signed minutes
    private Integer depDelayMin;
    private Integer arrDelayMin;

    private Boolean cancelled;
    private Boolean diverted;

    // Delay causes 
    // Carriers file these themselves, in minutes. They are what let us NAME a
    // cluster in Phase 4 instead of guessing at it. Null when not delayed.
    private Integer carrierDelay;
    private Integer weatherDelay;
    private Integer nasDelay;
    private Integer securityDelay;
    private Integer lateAircraftDelay;

    // Model features
    private Integer distance;
    private Integer dayOfWeek;
    private Integer month;
    private Integer taxiOut;
    private Integer taxiIn;

    private String source;  

    // Assigned by the Python pipeline in Phase 3. Null until then.
    private Integer clusterId;
}
