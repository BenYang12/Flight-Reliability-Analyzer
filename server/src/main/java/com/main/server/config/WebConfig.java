package com.main.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS: which web pages are allowed to call this API from a browser.
// Most browsers enforce "same-origin policy" -> page served from http://localhost:3000 may not read response from http://localhost:8081

// Configuration creates a bean
// This class allows my frontend on :3000 to call :8081
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.cors.allowed-origin:http://localhost:3000}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)

                //GET for every read endpoint, POST for /api/analyze
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
                // my application has no sessions/auth, so no .allowCrednetials(true) needed
    }
}
