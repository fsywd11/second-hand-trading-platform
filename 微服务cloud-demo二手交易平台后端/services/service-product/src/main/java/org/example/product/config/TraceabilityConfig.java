package org.example.product.config;

import org.example.trace.support.TraceabilityChainService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class TraceabilityConfig {

    @Bean
    public TraceabilityChainService traceabilityChainService(JdbcTemplate jdbcTemplate) {
        return new TraceabilityChainService(jdbcTemplate);
    }

    @Bean
    public ApplicationRunner traceabilitySchemaInitializer(TraceabilityChainService traceabilityChainService) {
        return args -> traceabilityChainService.initializeSchema();
    }
}
