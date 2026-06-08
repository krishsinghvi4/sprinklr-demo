package com.example.sprinklr.marketplace.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // Applies to your /api/v1/chat/stream endpoint
                .allowedOrigins("http://localhost:5173", "http://localhost:3000") // Trust Vite and standard React ports
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // OPTIONS is crucial for the preflight!
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}