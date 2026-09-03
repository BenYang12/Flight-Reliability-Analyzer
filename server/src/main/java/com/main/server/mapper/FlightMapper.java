package com.main.server.mapper;

import com.main.server.dto.FlightOperationDto;
import com.main.server.entity.Flight;

// Entity -> DTO, in one place.
//
// This is the only file in the project that knows how a Flight row becomes a
// FlightOperationDto. Putting it here rather than inline in the service means
// Checkpoint 6's Flask call and Checkpoint 7's OpenSky path convert flights the
// same way, instead of two hand-written conversions drifting apart.
//
// Every method is static and the class has no fields, so it is not a Spring bean
// and nothing needs to inject it. There is no state to hold.
public final class FlightMapper {

    // The FAA's definition of an on-time arrival, and the only hardcoded number
    // in this class. Everything else the project treats as a threshold comes
    // from corpus percentiles in thresholds.json instead.
    private static final int ON_TIME_MINUTES = 15;

    // Private constructor: this class is a bag of functions, not something you
    // instantiate. `new FlightMapper()` should not compile.
    private FlightMapper() {
    }

    public static FlightOperationDto toDto(Flight f) {
        return new FlightOperationDto(
                f.getFlightDate(),
                // Reading .getIata() on the LAZY airport association is exactly
                // what triggers the N+1 problem when the flights were loaded
                // without a fetch join. FlightRepository's @EntityGraph is what
                // makes these three lines free instead of three queries EACH.
                iataOf(f.getOrigin()),
                iataOf(f.getDest()),
                f.getCarrier() == null ? null : f.getCarrier().getName(),

                f.getCrsDepTime(),
                f.getDepTime(),
                f.getCrsArrTime(),
                f.getArrTime(),
                f.getCrsElapsedTime(),

                f.getDepDelayMin(),
                f.getArrDelayMin(),

                f.getTaxiOut(),
                f.getTaxiIn(),
                f.getDistance(),
                f.getDayOfWeek(),
                f.getMonth(),

                f.getCarrierDelay(),
                f.getWeatherDelay(),
                f.getNasDelay(),
                f.getSecurityDelay(),
                f.getLateAircraftDelay(),

                f.getCancelled(),
                f.getDiverted(),

                onTime(f)
        );
    }

    // Null rather than false for a flight that never arrived. A cancelled flight
    // was not "late" — it did not happen — and collapsing those two into `false`
    // would quietly drag every on-time rate downward.
    private static Boolean onTime(Flight f) {
        return f.getArrDelayMin() == null ? null : f.getArrDelayMin() <= ON_TIME_MINUTES;
    }

    private static String iataOf(com.main.server.entity.Airport airport) {
        return airport == null ? null : airport.getIata();
    }
}
