package com.main.server.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

public final class AnalyzeServiceDto {

    private AnalyzeServiceDto() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Row(
            Integer crsDepTime,
            Integer crsElapsedTime,
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
            Integer lateAircraftDelay
    ) {
    }

    public record Result(
            int cluster,
            String archetype,
            String description,
            @JsonAlias("on_time") boolean onTime,
            List<String> facts,
            String summary
    ) {
    }

    public record BatchRequest(List<Row> flights) {
    }

    public record BatchResponse(List<Result> results) {
    }
}
