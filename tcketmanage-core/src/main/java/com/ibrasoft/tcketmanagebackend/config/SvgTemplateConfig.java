package com.ibrasoft.tcketmanagebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * A Thymeleaf engine dedicated to rendering the ticket SVG templates. It is deliberately kept
 * separate from Spring Boot's auto-configured (HTML) {@code SpringTemplateEngine} used for emails:
 * SVG is XML, so it needs {@link TemplateMode#XML} to stay well-formed for the Batik transcoder,
 * and isolating it avoids touching the shared email engine's resolver chain.
 */
@Configuration
public class SvgTemplateConfig {

    @Bean
    public TemplateEngine svgTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".svg");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
