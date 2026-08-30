package com.main.server.service;

import com.main.server.dto.AirportDto;
import com.main.server.entity.Airport;
import com.main.server.repository.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;

// @Service contains Business logic for airports: run the search, cap the results, convert
// entities to DTOs. The controller stays free of all of this.
@Service
@RequiredArgsConstructor
public class AirportService {

    // A typeahead dropdown nobody scrolls past ten items in.
    private static final int MAX_RESULTS = 10;

    private final AirportRepository airportRepository;

    public List<AirportDto> search(String q) {
        // airportRepository.search(...)  searches aiport data repo
        // Limit.of(...) limits number of returned records
        // .stream() creates a java Stream for processing the results, which is a sequence of data elements that you can pump through a pipeline of functions to filter, transform, or aggregate data effortlessly.
        // .map() converts every airport entity into an AirportDto
        return airportRepository.search(q, Limit.of(MAX_RESULTS))
                .stream() 
                .map(AirportService::toDto)  // airport -> AirportService.toDto(airport)
                .toList();
    }

    private static AirportDto toDto(Airport a) {
        return new AirportDto(a.getIcao(), a.getIata(), a.getName(), a.getCity(), a.getLat(), a.getLon());
    }
}
