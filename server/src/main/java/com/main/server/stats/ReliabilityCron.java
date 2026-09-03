package com.main.server.stats;

import com.main.server.repository.RouteReliabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReliabilityCron {

    private final RouteReliabilityRepository routeReliabilityRepository;

    @Scheduled(cron = "${reliability.cron}")
    @Transactional
    public void recompute() {
        long startedAt = System.currentTimeMillis();
        int rows = routeReliabilityRepository.recomputeAll();
        log.info("route_reliability recomputed: {} rows in {} ms",
                rows, System.currentTimeMillis() - startedAt);
    }
}
