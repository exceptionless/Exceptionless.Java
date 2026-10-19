package com.exceptionless.spring.boot;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.servlet.ExceptionlessFilter;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Auto-configures Exceptionless. Add the starter and set the API key:
 *
 * <pre>{@code
 * exceptionless.api-key=YOUR_API_KEY
 * }</pre>
 *
 * <p>This registers:
 *
 * <ul>
 *   <li>an {@link ExceptionlessClient} bean, configured from the {@link ExceptionlessProperties
 *       exceptionless.*} properties and any {@link ExceptionlessConfigurationCustomizer} beans. It
 *       becomes the {@link Exceptionless#getDefault() default client} (used by logging integrations
 *       without their own API key) and is closed, sending queued events, when the application
 *       context closes;
 *   <li>in servlet web applications, an {@link ExceptionlessFilter} that reports unhandled
 *       exceptions and 404s with the request details, and adds the request to errors logged during
 *       it;
 *   <li>in reactive web applications, an {@link ExceptionlessWebFilter} that does the same for
 *       WebFlux;
 *   <li>when Logback is the logging system, an appender on the root logger that sends log messages
 *       at or above {@code exceptionless.logging.minimum-level} (default {@code warn}) and logged
 *       exceptions.
 * </ul>
 *
 * <p>Set {@code exceptionless.enabled=false} to turn all of it off.
 */
@AutoConfiguration
@ConditionalOnClass(ExceptionlessClient.class)
@ConditionalOnProperty(
    prefix = "exceptionless",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ExceptionlessProperties.class)
public class ExceptionlessAutoConfiguration {

  /** Creates and starts the client. */
  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public ExceptionlessClient exceptionlessClient(
      ExceptionlessProperties properties,
      ObjectProvider<ExceptionlessConfigurationCustomizer> customizers,
      ObjectProvider<BuildProperties> buildProperties) {
    ExceptionlessConfiguration config = ExceptionlessConfiguration.fromEnvironment();
    apply(properties, config);
    if (config.getVersion() == null) {
      BuildProperties build = buildProperties.getIfAvailable();
      if (build != null) {
        config.version(build.getVersion());
      }
    }
    // The application context closes the client.
    config.registerShutdownHook(false);
    customizers.orderedStream().forEach(customizer -> customizer.customize(config));

    ExceptionlessClient client = new ExceptionlessClient(config);
    Exceptionless.setDefault(client);
    return client.startup();
  }

  static void apply(ExceptionlessProperties properties, ExceptionlessConfiguration config) {
    if (StringUtils.hasText(properties.getApiKey())) {
      config.apiKey(properties.getApiKey());
    }
    if (StringUtils.hasText(properties.getServerUrl())) {
      config.serverUrl(properties.getServerUrl());
    }
    if (StringUtils.hasText(properties.getVersion())) {
      config.version(properties.getVersion());
    }
    config.addDefaultTags(properties.getDefaultTags().toArray(String[]::new));
    for (Map.Entry<String, Object> entry : properties.getDefaultData().entrySet()) {
      config.addDefaultData(entry.getKey(), entry.getValue());
    }
    properties.getSettings().forEach(config::setting);
    if (properties.getIncludePrivateInformation() != null) {
      config.includePrivateInformation(properties.getIncludePrivateInformation());
    }
    if (StringUtils.hasText(properties.getStoragePath())) {
      config.useFileStorage(Path.of(properties.getStoragePath()));
    }
    if (properties.isUseSessions()) {
      config.useSessions();
    }
    config.captureUncaughtExceptions(properties.isCaptureUncaughtExceptions());
  }

  /**
   * Makes the client the {@link Exceptionless#getDefault() default client}, including a client bean
   * defined by the application, and clears it again when the context closes.
   */
  @Bean
  DefaultClientRegistration exceptionlessDefaultClientRegistration(ExceptionlessClient client) {
    return new DefaultClientRegistration(client);
  }

  static final class DefaultClientRegistration implements DisposableBean {
    private final ExceptionlessClient client;

    DefaultClientRegistration(ExceptionlessClient client) {
      this.client = client;
      Exceptionless.setDefault(client);
    }

    @Override
    public void destroy() {
      if (Exceptionless.hasDefault() && Exceptionless.getDefault() == client) {
        Exceptionless.setDefault(null);
      }
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(
      name = {"jakarta.servlet.Filter", "com.exceptionless.servlet.ExceptionlessFilter"})
  static class ServletConfiguration {
    /** Reports unhandled exceptions and 404s. Spring Boot registers Filter beans automatically. */
    @Bean
    @ConditionalOnMissingBean(ExceptionlessFilter.class)
    OrderedExceptionlessFilter exceptionlessFilter(
        ExceptionlessClient client, ExceptionlessProperties properties) {
      OrderedExceptionlessFilter filter =
          new OrderedExceptionlessFilter(client, properties.getFilterOrder());
      filter.setCaptureNotFound(properties.isCaptureNotFound());
      return filter;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  @ConditionalOnClass(
      name = {"org.springframework.web.server.WebFilter", "reactor.core.publisher.Mono"})
  static class ReactiveConfiguration {
    /** Reports unhandled exceptions and 404s. */
    @Bean
    @ConditionalOnMissingBean
    ExceptionlessWebFilter exceptionlessWebFilter(
        ExceptionlessClient client, ExceptionlessProperties properties) {
      return new ExceptionlessWebFilter(
          client, properties.isCaptureNotFound(), properties.getFilterOrder());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = {
        "ch.qos.logback.classic.LoggerContext",
        "com.exceptionless.logback.ExceptionlessAppender"
      })
  @ConditionalOnProperty(
      prefix = "exceptionless.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class LogbackConfiguration {
    /** Adds the Exceptionless appender to the Logback root logger. */
    @Bean
    @ConditionalOnMissingBean
    LogbackAppenderRegistration exceptionlessLogbackAppenderRegistration(
        ExceptionlessClient client, ExceptionlessProperties properties) {
      return new LogbackAppenderRegistration(client, properties.getLogging().getMinimumLevel());
    }
  }
}
