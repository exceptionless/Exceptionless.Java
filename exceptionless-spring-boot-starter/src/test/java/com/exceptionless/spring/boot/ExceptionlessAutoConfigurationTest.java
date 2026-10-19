package com.exceptionless.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.logback.ExceptionlessAppender;
import com.exceptionless.servlet.ExceptionlessFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import reactor.core.publisher.Mono;

class ExceptionlessAutoConfigurationTest {
  private static final AutoConfigurations AUTO_CONFIGURATION =
      AutoConfigurations.of(ExceptionlessAutoConfiguration.class);

  private final CapturingSubmissionClient recorder = new CapturingSubmissionClient();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AUTO_CONFIGURATION)
          .withPropertyValues(
              "exceptionless.api-key=" + TestClients.API_KEY,
              "exceptionless.capture-uncaught-exceptions=false")
          .withBean(
              "recordingCustomizer",
              ExceptionlessConfigurationCustomizer.class,
              () -> config -> config.submissionClient(recorder));

  @AfterEach
  void resetDefault() {
    Exceptionless.setDefault(null);
  }

  @Test
  void createsStartedClientFromProperties(@TempDir Path storage) {
    runner
        .withPropertyValues(
            "exceptionless.storage-path=" + storage,
            "exceptionless.server-url=http://localhost:5200/",
            "exceptionless.version=1.2.3",
            "exceptionless.default-tags=web,api",
            "exceptionless.default-data.team=core",
            "exceptionless.include-private-information=false",
            "exceptionless.use-sessions=true")
        // ':' separates keys from values in withPropertyValues, so add the bracketed map key
        // directly.
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "exceptionlessSettings",
                            Map.of("exceptionless.settings.[@@log:com.example.*]", "info"))))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ExceptionlessClient.class);
              ExceptionlessClient client = context.getBean(ExceptionlessClient.class);
              assertThat(client.isStarted()).isTrue();
              ExceptionlessConfiguration config = client.getConfiguration();
              assertThat(config.getApiKey()).isEqualTo(TestClients.API_KEY);
              assertThat(context.getBean(ExceptionlessProperties.class).getStoragePath())
                  .isEqualTo(storage);
              assertThat(config.getStoragePath()).isEqualTo(storage);
              assertThat(config.getServerUrl()).isEqualTo("http://localhost:5200");
              assertThat(config.getVersion()).isEqualTo("1.2.3");
              assertThat(config.getDefaultTags()).containsExactlyInAnyOrder("web", "api");
              assertThat(config.getDefaultData()).containsEntry("team", "core");
              assertThat(config.getSettings().asMap()).containsEntry("@@log:com.example.*", "info");
              assertThat(config.isIncludePrivateInformation()).isFalse();
              assertThat(config.isIncludeCookies()).isFalse();
              assertThat(config.isSessionsEnabled()).isTrue();
              assertThat(config.isCaptureUncaughtExceptions()).isFalse();
              assertThat(config.isRegisterShutdownHook()).isFalse();
            });
  }

  @Test
  void leavesPrivateInformationDefaultWhenNotSet() {
    runner.run(
        context ->
            assertThat(
                    context
                        .getBean(ExceptionlessClient.class)
                        .getConfiguration()
                        .isIncludePrivateInformation())
                .isTrue());
  }

  @Test
  void usesBuildVersionWhenNoVersionIsSet() {
    runner
        .withBean(BuildProperties.class, () -> buildProperties("4.5.6"))
        .run(
            context ->
                assertThat(
                        context.getBean(ExceptionlessClient.class).getConfiguration().getVersion())
                    .isEqualTo("4.5.6"));

    runner
        .withBean(BuildProperties.class, () -> buildProperties("4.5.6"))
        .withPropertyValues("exceptionless.version=1.0.0")
        .run(
            context ->
                assertThat(
                        context.getBean(ExceptionlessClient.class).getConfiguration().getVersion())
                    .isEqualTo("1.0.0"));
  }

  @Test
  void appliesCustomizersInOrder() {
    runner
        .withUserConfiguration(CustomizerConfiguration.class)
        .run(
            context -> {
              ExceptionlessConfiguration config =
                  context.getBean(ExceptionlessClient.class).getConfiguration();
              assertThat(config.getDefaultTags()).contains("first", "second");
              assertThat(config.getVersion()).isEqualTo("second");
            });
  }

  @Test
  void becomesTheDefaultClientAndClosesWithTheContext() {
    AtomicReference<ExceptionlessClient> client = new AtomicReference<>();
    runner.run(
        context -> {
          client.set(context.getBean(ExceptionlessClient.class));
          assertThat(Exceptionless.hasDefault()).isTrue();
          assertThat(Exceptionless.getDefault()).isSameAs(client.get());
        });
    assertThat(client.get().isStarted()).isFalse();
    assertThat(Exceptionless.hasDefault()).isFalse();
  }

  @Test
  void backsOffWhenTheApplicationDefinesAClient() {
    ExceptionlessClient own =
        new ExceptionlessClient(new ExceptionlessConfiguration().submissionClient(recorder));
    runner
        .withBean("myClient", ExceptionlessClient.class, () -> own)
        .run(
            context -> {
              assertThat(context).getBean(ExceptionlessClient.class).isSameAs(own);
              assertThat(Exceptionless.getDefault()).isSameAs(own);
            });
  }

  @Test
  void disabledTurnsEverythingOff() {
    new WebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withPropertyValues(
            "exceptionless.enabled=false", "exceptionless.api-key=" + TestClients.API_KEY)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ExceptionlessClient.class);
              assertThat(context).doesNotHaveBean(ExceptionlessFilter.class);
              assertThat(context).doesNotHaveBean(LogbackAppenderRegistration.class);
              assertThat(Exceptionless.hasDefault()).isFalse();
            });
  }

  @Test
  void registersServletFilterInServletApplications() {
    new WebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withPropertyValues(
            "exceptionless.api-key=" + TestClients.API_KEY,
            "exceptionless.capture-uncaught-exceptions=false",
            "exceptionless.capture-not-found=false",
            "exceptionless.filter-order=42")
        .run(
            context -> {
              assertThat(context).hasSingleBean(OrderedExceptionlessFilter.class);
              assertThat(context).doesNotHaveBean(ExceptionlessWebFilter.class);
              OrderedExceptionlessFilter filter = context.getBean(OrderedExceptionlessFilter.class);
              assertThat(filter.getOrder()).isEqualTo(42);
              assertThat(filter.isCaptureNotFound()).isFalse();
              assertThat(filter.getClient()).isSameAs(context.getBean(ExceptionlessClient.class));
            });
  }

  @Test
  void servletFilterDefaultsRunAfterSpringSecurity() {
    new WebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withPropertyValues(
            "exceptionless.api-key=" + TestClients.API_KEY,
            "exceptionless.capture-uncaught-exceptions=false")
        .run(
            context -> {
              OrderedExceptionlessFilter filter = context.getBean(OrderedExceptionlessFilter.class);
              assertThat(filter.getOrder()).isEqualTo(-99);
              assertThat(filter.isCaptureNotFound()).isTrue();
            });
  }

  @Test
  void registersWebFilterInReactiveApplications() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withPropertyValues(
            "exceptionless.api-key=" + TestClients.API_KEY,
            "exceptionless.capture-uncaught-exceptions=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ExceptionlessWebFilter.class);
              assertThat(context).doesNotHaveBean(ExceptionlessFilter.class);
              assertThat(context.getBean(ExceptionlessWebFilter.class).getOrder()).isEqualTo(-99);
            });
  }

  @Test
  void backsOffFromTheServletFilterWithoutExceptionlessServlet() {
    new WebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withClassLoader(new FilteredClassLoader(ExceptionlessFilter.class))
        .withPropertyValues(
            "exceptionless.api-key=" + TestClients.API_KEY,
            "exceptionless.capture-uncaught-exceptions=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ExceptionlessClient.class);
              assertThat(context).doesNotHaveBean(OrderedExceptionlessFilter.class);
            });
  }

  @Test
  void backsOffFromTheWebFilterWithoutReactor() {
    new ReactiveWebApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGURATION)
        .withClassLoader(new FilteredClassLoader(Mono.class))
        .withPropertyValues(
            "exceptionless.api-key=" + TestClients.API_KEY,
            "exceptionless.capture-uncaught-exceptions=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ExceptionlessClient.class);
              assertThat(context).doesNotHaveBean(ExceptionlessWebFilter.class);
            });
  }

  @Test
  void backsOffFromLogbackWithoutLogback() {
    runner
        .withClassLoader(new FilteredClassLoader(LoggerContext.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ExceptionlessClient.class);
              assertThat(context).doesNotHaveBean(LogbackAppenderRegistration.class);
            });
  }

  @Test
  void bindsTheMinimumLevelCaseInsensitively() {
    runner
        .withPropertyValues("exceptionless.logging.minimum-level=ERROR")
        .run(
            context -> {
              assertThat(
                      context.getBean(ExceptionlessProperties.class).getLogging().getMinimumLevel())
                  .isEqualTo(LogLevel.ERROR);
              assertThat(
                      context
                          .getBean(LogbackAppenderRegistration.class)
                          .getAppender()
                          .getMinimumLevel())
                  .isEqualTo(LogLevel.ERROR);
            });
  }

  @Test
  void registersNoWebFiltersInNonWebApplications() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(ExceptionlessFilter.class);
          assertThat(context).doesNotHaveBean(ExceptionlessWebFilter.class);
        });
  }

  @Test
  void addsLogbackAppenderWhileTheContextRuns() {
    AtomicReference<ExceptionlessAppender> appender = new AtomicReference<>();
    runner
        .withPropertyValues("exceptionless.logging.minimum-level=info")
        .run(
            context -> {
              ExceptionlessClient client = context.getBean(ExceptionlessClient.class);
              appender.set(context.getBean(LogbackAppenderRegistration.class).getAppender());
              assertThat(appender.get()).isNotNull();
              assertThat(appender.get().getClient()).isSameAs(client);
              assertThat(appender.get().getMinimumLevel()).isEqualTo(LogLevel.INFO);
              assertThat(rootAppenders()).contains(appender.get());

              LoggerFactory.getLogger("com.example.Checkout")
                  .error("Payment failed", new IllegalStateException("declined"));
              LoggerFactory.getLogger("com.example.Checkout").info("Order placed");
              LoggerFactory.getLogger("com.example.Checkout").debug("Not sent");

              recorder.awaitEvents(client, 2);
              List<Map<String, Object>> events = recorder.flush(client);
              assertThat(events)
                  .anySatisfy(
                      e -> assertThat(TestClients.error(e)).containsEntry("message", "declined"))
                  .anySatisfy(
                      e ->
                          assertThat(e)
                              .containsEntry("type", "log")
                              .containsEntry("message", "Order placed"))
                  .noneSatisfy(e -> assertThat(e).containsEntry("message", "Not sent"));
            });
    assertThat(rootAppenders()).doesNotContain(appender.get());
    assertThat(appender.get().isStarted()).isFalse();
  }

  @Test
  void addsTheAppenderAgainWhenLogbackIsReset() throws Exception {
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    AtomicReference<LogbackAppenderRegistration> registration = new AtomicReference<>();
    try {
      runner.run(
          context -> {
            ExceptionlessClient client = context.getBean(ExceptionlessClient.class);
            registration.set(context.getBean(LogbackAppenderRegistration.class));
            ExceptionlessAppender original = registration.get().getAppender();
            assertThat(rootAppenders()).contains(original);

            // What Logback does when it reloads a changed logback.xml (scan="true").
            loggerContext.reset();

            ExceptionlessAppender replacement = registration.get().getAppender();
            assertThat(replacement).isNotNull().isNotSameAs(original);
            assertThat(replacement.isStarted()).isTrue();
            assertThat(original.isStarted()).isFalse();
            assertThat(rootAppenders()).contains(replacement).doesNotContain(original);

            LoggerFactory.getLogger("com.example.Reset").error("After reset");
            assertThat(recorder.awaitEvents(client, 1))
                .extracting(e -> e.get("message"))
                .contains("After reset");
          });
      assertThat(rootAppenders())
          .noneMatch(a -> LogbackAppenderRegistration.APPENDER_NAME.equals(a.getName()));
      assertThat(loggerContext.getCopyOfListenerList()).doesNotContain(registration.get());
    } finally {
      // Restore the default configuration (logback-test.xml or Logback's defaults).
      loggerContext.reset();
      new ContextInitializer(loggerContext).autoConfig();
    }
  }

  @Test
  void loggingCanBeDisabled() {
    runner
        .withPropertyValues("exceptionless.logging.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(LogbackAppenderRegistration.class));
  }

  private static List<Appender<ILoggingEvent>> rootAppenders() {
    Logger root =
        ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
    for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
      appenders.add(it.next());
    }
    return appenders;
  }

  private static BuildProperties buildProperties(String version) {
    Properties properties = new Properties();
    properties.setProperty("version", version);
    return new BuildProperties(properties);
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  static class CustomizerConfiguration {
    @org.springframework.context.annotation.Bean
    @Order(2)
    ExceptionlessConfigurationCustomizer second() {
      return config -> config.addDefaultTags("second").version("second");
    }

    @org.springframework.context.annotation.Bean
    @Order(1)
    ExceptionlessConfigurationCustomizer first() {
      return config -> config.addDefaultTags("first").version("first");
    }
  }
}
