package com.main.server.dto;

import com.main.server.dto.AnalyzeServiceDto.Result;

import java.time.LocalDate;

// One historical BTS operation, as the API hands it out.
public record FlightOperationDto(
        LocalDate flightDate,
        String origin,        
        String dest,
        String carrierName,  

       
        Integer crsDepTime,   
        Integer depTime,      
        Integer crsArrTime,
        Integer arrTime,
        Integer crsElapsedTime, // scheduled gate-to-gate minutes
        Integer depDelayMin,
        Integer arrDelayMin,

        Integer taxiOut,
        Integer taxiIn,
        Integer distance,
        Integer dayOfWeek,
        Integer month,
        Integer carrierDelay,
        Integer weatherDelay,
        Integer nasDelay,
        Integer securityDelay,
        Integer lateAircraftDelay,

        Boolean cancelled,
        Boolean diverted,

        Boolean onTime,

        Result analysis
) {
}
