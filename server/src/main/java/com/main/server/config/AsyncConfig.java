package com.main.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// Declares thread pool that concurrent OpenSky fetches will run on. 

// @EnableAsync turns on @Async. A method marked with @Async returns to its caller immediately and runs on background thread
// @EnableScheduling turns on @Scheduled, which I will need for nightly ReliabilityCron.


@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    // create own pool b/c Spring Boot's default is unbounded


    // @Async("openSkyExecutor") will find this bean later
    @Bean
    public Executor openSkyExecutor() {
        // ThreadPoolTaskExecutor is a spring-managed JavaBean wrapper used to manage pool of worker threads
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

       
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);

        // Tasks wait here when all core threads are busy. Bounded to 50
        executor.setQueueCapacity(50);

        // Prefixes thread names in the logs ("opensky-1") for easier debugging. 
        executor.setThreadNamePrefix("opensky-");

        // On shutdown, finish in-flight fetches rather than killing them
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);

        executor.initialize();
        return executor;
    }
}
