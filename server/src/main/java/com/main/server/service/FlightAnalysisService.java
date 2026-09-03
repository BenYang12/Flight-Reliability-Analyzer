package com.main.server.service;

import com.main.server.dto.AnalyzeServiceDto.BatchRequest;
import com.main.server.dto.AnalyzeServiceDto.BatchResponse;
import com.main.server.dto.AnalyzeServiceDto.Result;
import com.main.server.dto.AnalyzeServiceDto.Row;
import com.main.server.dto.FlightAnalysisRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class FlightAnalysisService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public FlightAnalysisService(RestClient.Builder builder,
                                 @Value("${analyzer.base-url}") String baseUrl,
                                 @Value("${analyzer.timeout-seconds}") int timeoutSeconds) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Optional<Result> analyze(FlightAnalysisRequest request) {
        Row row = new Row(
                request.crsDepTime(),
                request.crsElapsedTime(),
                request.depDelayMin(),
                request.arrDelayMin(),
                request.taxiOut(),
                request.taxiIn(),
                request.distance(),
                request.dayOfWeek(),
                request.month(),
                request.carrierDelay(),
                request.weatherDelay(),
                request.nasDelay(),
                request.lateAircraftDelay());

        try {
            return Optional.ofNullable(client.post()
                    .uri("/analyze")
                    .body(row)
                    .retrieve()
                    .body(Result.class));
        } catch (RestClientException e) {
            log.warn("analyzer /analyze unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Result> analyzeBatch(List<Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        try {
            BatchResponse response = client.post()
                    .uri("/analyze-batch")
                    .body(new BatchRequest(rows))
                    .retrieve()
                    .body(BatchResponse.class);

            if (response == null || response.results() == null
                    || response.results().size() != rows.size()) {
                log.warn("analyzer /analyze-batch returned an unusable response");
                return List.of();
            }

            return response.results();
        } catch (RestClientException e) {
            log.warn("analyzer /analyze-batch unavailable: {}", e.getMessage());
            return List.of();
        }
    }
}
