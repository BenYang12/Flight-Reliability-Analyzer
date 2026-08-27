package com.main.server.config;

import com.main.server.entity.Airport;
import com.main.server.entity.Carrier;
import com.main.server.repository.AirportRepository;
import com.main.server.repository.CarrierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Loads the airport and airline CSV data into the database when Spring application starts
// Spring application starts -> DataSeeder.run() -> Read airports.csv and carriers.csv -> turn each CSV row into a Java object -> save those objects into PostgreSQL


@Component //tells spring to create and manage a DataSeeder Object
@RequiredArgsConstructor // Lombok builds the constructor Spring injects through.
@Slf4j                   // Lombok gives us the `log` field.
public class DataSeeder implements CommandLineRunner {

    // Constructor injection
    // object cannot exist in a half-built state, and it stays testable.
    private final AirportRepository airportRepository;
    private final CarrierRepository carrierRepository;

    @Override
    public void run(String... args) throws IOException {
        seedAirports();
        seedCarriers();
    }

    private void seedAirports() throws IOException {
        // Idempotency: the app restarts constantly under DevTools, and this must not re-insert or fail on a second run.
        if (airportRepository.count() > 0) {
            log.info("Airports already seeded ({} rows), skipping!", airportRepository.count());
            return;
        }

        List<Airport> airports = new ArrayList<>();
        for (String[] f : readCsv("data/airports.csv")) {
            Airport a = new Airport();
            a.setIcao(f[0]);
            a.setIata(f[1]);
            a.setName(f[2]);
            a.setCity(f[3]);
            a.setCountry(f[4]);
            a.setLat(Double.parseDouble(f[5]));
            a.setLon(Double.parseDouble(f[6]));
            airports.add(a);
        }

        // One saveAll rather than 609 individual save() calls.
        airportRepository.saveAll(airports);
        log.info("Seeded {} airports.", airports.size());
    }

    private void seedCarriers() throws IOException {
        if (carrierRepository.count() > 0) {
            log.info("Carriers already seeded ({} rows) — skipping.", carrierRepository.count());
            return;
        }

        List<Carrier> carriers = new ArrayList<>();
        for (String[] f : readCsv("data/carriers.csv")) {
            Carrier c = new Carrier();
            c.setIcao(f[0]);
            c.setIata(f[1]);
            c.setName(f[2]);
            carriers.add(c);
        }

        carrierRepository.saveAll(carriers);
        log.info("Seeded {} carriers.", carriers.size());
    }

    // Reads a CSV from src/main/resources, skipping the header row.
    // ClassPathResource works whether we run from Maven or from a packaged
    // jar, where the file is inside the archive and has no filesystem path.
    private List<String[]> readCsv(String resourcePath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); 
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(splitCsvLine(line));
                }
            }
        }
        return rows;
    }
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
        fields.add(current.toString()); // the final field has no trailing comma

        return fields.toArray(new String[0]);
    }
}
