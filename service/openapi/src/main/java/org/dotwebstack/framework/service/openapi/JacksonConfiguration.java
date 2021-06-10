package org.dotwebstack.framework.service.openapi;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.zalando.problem.ProblemModule;

@Configuration
public class JacksonConfiguration {

  private final OpenApiProperties openApiProperties;

  public JacksonConfiguration(OpenApiProperties openApiProperties) {
    this.openApiProperties = openApiProperties;
  }

  @Bean
  public Module javaTimeModule() {
    return new JavaTimeModule();
  }

  @Bean
  public Module problemModule() {
    return new ProblemModule();
  }

  @Bean
  @Qualifier("default")
  public Jackson2ObjectMapperBuilder objectMapperBuilder(List<Module> modules) {
    var builder = new Jackson2ObjectMapperBuilder();
    builder.featuresToEnable(SerializationFeature.INDENT_OUTPUT)
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .modules(modules);
    if (!openApiProperties.isSerializeNull()) {
      builder.serializationInclusion(Include.NON_NULL);
    }
    return builder;
  }
}
