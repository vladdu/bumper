package com.volvo.bumper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Provides a shared {@link ObjectMapper} bean used across all adapters and CLI commands. */
@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }
}
