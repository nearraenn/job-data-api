package com.ata.jobdata.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Tomcat rejects a raw {@code [} or {@code ]} in a query string with 400 before the request ever
     * reaches Spring, which would break the {@code ?salary[gte]=120000} syntax unless every client
     * remembered to percent-encode the brackets. RFC 3986 lists them as reserved rather than illegal,
     * and this only widens what the query string accepts, so the filter syntax works as written.
     */
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> allowBracketsInQueryString() {
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setProperty("relaxedQueryChars", "[]"));
    }

    /** Lets the frontend exercise call this API from a Vite/CRA dev server. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET");
    }
}
