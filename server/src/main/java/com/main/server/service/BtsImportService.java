package com.main.server.service;

import com.main.server.entity.Airport;
import com.main.server.entity.Carrier;
import com.main.server.entity.Flight;
import com.main.server.repository.AirportRepository;
import com.main.server.repository.CarrierRepository;
import com.main.server.repository.FlightRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

// This service performs incremental imports of historical flight data from BTS into PostgreSQL


// The one-time bulk load of all four months is ml/get_data.py, which uses
// Postgres COPY and finishes 871k rows in about 14 seconds.

// This class exists for the smaller, ongoing case — a corrected day, a newly published month —
// where the data has to flow through the same entity model the rest of the API
// uses, rather than around it.

// It is also where JPA's abstraction leaks: persisting rows one at a time is
// unusably slow, and the fix (batch_size + flush-and-clear) is not something
// the ORM does for you.
@Service
@RequiredArgsConstructor
@Slf4j
public class BtsImportService {

    // Must match spring.jpa.properties.hibernate.jdbc.batch_size.
    private static final int BATCH_SIZE = 50;

    // Same 30 airports ml/get_data.py filters to, and for the same reason:
    // both endpoints must be in scope or route statistics come out lopsided.
    private static final Set<String> TOP_30 = Set.of(
            "ATL", "DFW", "DEN", "ORD", "LAX", "CLT", "MCO", "LAS", "PHX", "MIA",
            "SEA", "IAH", "JFK", "EWR", "FLL", "MSP", "SFO", "DTW", "BOS", "SLC",
            "PHL", "BWI", "TPA", "SAN", "LGA", "MDW", "BNA", "IAD", "DCA", "AUS");

    private final FlightRepository flightRepository;
    private final AirportRepository airportRepository;
    private final CarrierRepository carrierRepository;

    // The EntityManager is JPA's lower-level handle on the persistence context.
    // Spring Data's repositories are built on it, but flush() and clear() are
    // not exposed there, and this import needs both.
    @PersistenceContext
    private EntityManager entityManager;

    // @Transactional: everything below either commits together or rolls back
    // together. A half-imported day would leave the statistics wrong with no
    // signal that anything failed.
    @Transactional
    public int importFile(Path path) throws IOException {
        long startedAt = System.currentTimeMillis();

        List<Flight> flights = parse(path);
        if (flights.isEmpty()) {
            log.warn("No in-scope rows found in {}", path.getFileName());
            return 0;
        }

        // Rewrite the window this file covers rather than appending to it, so
        // running the same import twice is safe.
        LocalDate start = flights.stream().map(Flight::getFlightDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = flights.stream().map(Flight::getFlightDate).max(LocalDate::compareTo).orElseThrow();
        int deleted = flightRepository.deleteBtsRowsBetween(start, end);
        log.info("Refreshing {} .. {} — cleared {} existing BTS rows", start, end, deleted);

        for (int i = 0; i < flights.size(); i++) {
            entityManager.persist(flights.get(i));

            // The two lines that make this fast.
            //   flush() — hand the queued INSERTs to the JDBC driver, which
            //             sends them as one batch of BATCH_SIZE.
            //   clear() — detach everything from the persistence context.
            // Without clear(), Hibernate keeps every entity it has ever seen in
            // memory and re-checks all of them on each flush, so the import gets
            // progressively slower and eventually runs out of heap.
            if ((i + 1) % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();

        long millis = System.currentTimeMillis() - startedAt;
        log.info("Imported {} flights in {} ms ({} rows/sec)",
                flights.size(), millis, flights.size() * 1000L / Math.max(millis, 1));
        return flights.size();
    }

    // Reads the BTS file into entities. Nothing here touches the database.
    private List<Flight> parse(Path path) throws IOException {
        List<Flight> flights = new ArrayList<>();

        // Resolve the ICAO/IATA bridge tables ONCE, up front. Looking up each
        // row's airport and carrier individually would be the N+1 problem in
        // its write-side form: ~21,000 extra SELECTs for a single day of flights.
        Map<String, Airport> airportsByIata = airportRepository.findAll().stream()
                .filter(a -> a.getIata() != null)
                .collect(Collectors.toMap(Airport::getIata, Function.identity()));
        Map<String, Carrier> carriersByIata = carrierRepository.findAll().stream()
                .filter(c -> c.getIata() != null)
                .collect(Collectors.toMap(Carrier::getIata, Function.identity()));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(openCsv(path), StandardCharsets.UTF_8))) {

            // BTS files carry 110+ columns and their order is not guaranteed
            // across releases, so we address them by NAME via the header rather
            // than by a hardcoded position.
            String[] header = splitCsvLine(reader.readLine());
            Map<String, Integer> column = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                column.put(header[i], i);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = splitCsvLine(line);

                String origin = get(f, column, "Origin");
                String dest = get(f, column, "Dest");
                if (!TOP_30.contains(origin) || !TOP_30.contains(dest)) {
                    continue;
                }

                String carrierIata = get(f, column, "IATA_CODE_Reporting_Airline");
                Carrier carrier = carriersByIata.get(carrierIata);
                if (carrier == null) {
                    // Would violate the carrier_iata foreign key. Skipping is
                    // correct, but silence is not — a growing count here means
                    // the carriers seed has gone stale.
                    continue;
                }

                Flight flight = new Flight();
                flight.setFlightNumber(carrierIata + get(f, column, "Flight_Number_Reporting_Airline"));
                flight.setCarrier(carrier);
                flight.setOrigin(airportsByIata.get(origin));
                flight.setDest(airportsByIata.get(dest));
                flight.setFlightDate(LocalDate.parse(get(f, column, "FlightDate")));

                flight.setCrsDepTime(toInt(get(f, column, "CRSDepTime")));
                flight.setDepTime(toInt(get(f, column, "DepTime")));
                flight.setCrsArrTime(toInt(get(f, column, "CRSArrTime")));
                flight.setArrTime(toInt(get(f, column, "ArrTime")));

                // Scheduled block minutes. The model service divides arrival
                // delay by this, so an incremental import that skipped it would
                // produce rows the clusterer cannot score.
                flight.setCrsElapsedTime(toInt(get(f, column, "CRSElapsedTime")));

                // The signed columns, not the *Minutes variants, which floor at
                // zero and would erase every early departure.
                flight.setDepDelayMin(toInt(get(f, column, "DepDelay")));
                flight.setArrDelayMin(toInt(get(f, column, "ArrDelay")));

                flight.setCancelled(toBool(get(f, column, "Cancelled")));
                flight.setDiverted(toBool(get(f, column, "Diverted")));

                // Null unless the flight arrived 15+ minutes late. Left null
                // rather than zero-filled: "no weather delay" and "not delayed
                // enough to be reportable" are different facts.
                flight.setCarrierDelay(toInt(get(f, column, "CarrierDelay")));
                flight.setWeatherDelay(toInt(get(f, column, "WeatherDelay")));
                flight.setNasDelay(toInt(get(f, column, "NASDelay")));
                flight.setSecurityDelay(toInt(get(f, column, "SecurityDelay")));
                flight.setLateAircraftDelay(toInt(get(f, column, "LateAircraftDelay")));

                flight.setDistance(toInt(get(f, column, "Distance")));
                flight.setDayOfWeek(toInt(get(f, column, "DayOfWeek")));
                flight.setMonth(toInt(get(f, column, "Month")));
                flight.setTaxiOut(toInt(get(f, column, "TaxiOut")));
                flight.setTaxiIn(toInt(get(f, column, "TaxiIn")));

                // Historical fact with a real scheduled time. Never OPENSKY.
                flight.setSource("BTS");

                flights.add(flight);
            }
        }
        return flights;
    }

    // BTS ships either a plain .csv or a .zip holding the CSV plus a readme.html,
    // so a zip has to be opened by entry rather than read straight through.
    private static InputStream openCsv(Path path) throws IOException {
        if (!path.toString().endsWith(".zip")) {
            return Files.newInputStream(path);
        }
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(path));
        var entry = zip.getNextEntry();
        while (entry != null) {
            if (entry.getName().toLowerCase().endsWith(".csv")) {
                return zip;
            }
            entry = zip.getNextEntry();
        }
        throw new IOException("No .csv entry inside " + path);
    }

    private static String get(String[] fields, Map<String, Integer> column, String name) {
        Integer index = column.get(name);
        return (index == null || index >= fields.length) ? "" : fields[index];
    }

    // BTS writes whole numbers as decimals ("7.00"), so parseInt would throw.
    private static Integer toInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (int) Double.parseDouble(value);
    }

    private static Boolean toBool(String value) {
        return value != null && !value.isBlank() && Double.parseDouble(value) != 0.0;
    }

    // Quote-aware, because BTS city names contain commas ("San Antonio, TX").
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char ch : line.toCharArray()) {
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }
}
