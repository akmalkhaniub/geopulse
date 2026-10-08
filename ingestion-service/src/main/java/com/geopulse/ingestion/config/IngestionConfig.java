package com.geopulse.ingestion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
public class IngestionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }
}
