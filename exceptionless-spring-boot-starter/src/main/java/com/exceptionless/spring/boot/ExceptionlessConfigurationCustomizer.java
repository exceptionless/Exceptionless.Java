package com.exceptionless.spring.boot;

import com.exceptionless.ExceptionlessConfiguration;

/**
 * Customizes the auto-configured client's {@link ExceptionlessConfiguration} after the {@code
 * exceptionless.*} properties are applied and before the client starts. Declare one or more as
 * beans; they run in {@link org.springframework.core.annotation.Order @Order}.
 *
 * <pre>{@code
 * @Bean
 * ExceptionlessConfigurationCustomizer exceptionlessCustomizer() {
 *   return config -> config
 *       .addDataExclusions("*password*")
 *       .addEventExclusion(event -> "health".equals(event.getSource()));
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ExceptionlessConfigurationCustomizer {
  void customize(ExceptionlessConfiguration configuration);
}
