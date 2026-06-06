package com.ibrasoft.tcketmanagebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Central web configuration. Holds CORS in one place (allowed origins configurable per deployment via
 * {@code web.cors.allowed-origins}) so controllers no longer each carry {@code @CrossOrigin}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${web.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
