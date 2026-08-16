package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanage.autoconfigure.TcketManageProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@AllArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TcketManageProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(properties.getBasePath() + "/**")
                .allowedOrigins(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
