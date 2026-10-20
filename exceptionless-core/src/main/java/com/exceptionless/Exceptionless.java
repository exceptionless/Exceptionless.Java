package com.exceptionless;

import java.time.Duration;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The shared default {@link ExceptionlessClient}. Most applications only need this class:
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *   Exceptionless.startup("YOUR_API_KEY");
 *
 *   Exceptionless.submitLog("App started");
 *   try {
 *     run();
 *   } catch (Exception e) {
 *     Exceptionless.submitException(e);
 *   }
 * }
 * }</pre>
 *
 * <p>The default client is configured from the environment ({@code exceptionless.properties},
 * {@code EXCEPTIONLESS_API_KEY}/{@code EXCEPTIONLESS_SERVER_URL} and {@code exceptionless.*} system
 * properties), so {@code Exceptionless.startup()} with no arguments is enough when the API key is
 * provided that way. Logging integrations without their own API key use this client.
 */
public final class Exceptionless {
  private static final Object LOCK = new Object();
  private static volatile @Nullable ExceptionlessClient defaultClient;

  private Exceptionless() {}

  /** {@return the default client, created (but not started) on first use} */
  public static ExceptionlessClient getDefault() {
    ExceptionlessClient client = defaultClient;
    if (client == null) {
      synchronized (LOCK) {
        client = defaultClient;
        if (client == null) {
          client = new ExceptionlessClient(ExceptionlessConfiguration.fromEnvironment());
          defaultClient = client;
        }
      }
    }
    return client;
  }

  /** True when a default client exists (it may not have started yet). */
  public static boolean hasDefault() {
    return defaultClient != null;
  }

  /**
   * Replaces the default client, for example with one created by a dependency injection container.
   * The previous default client is not closed.
   */
  public static void setDefault(@Nullable ExceptionlessClient client) {
    synchronized (LOCK) {
      defaultClient = client;
    }
  }

  /** Starts the default client using the configuration from the environment. */
  public static ExceptionlessClient startup() {
    return getDefault().startup();
  }

  /** Starts the default client for the project with the given API key. */
  public static ExceptionlessClient startup(String apiKey) {
    return startup(config -> config.apiKey(apiKey));
  }

  /**
   * Configures and starts the default client.
   *
   * <p>If the default client was already started without a valid configuration (for example because
   * something used it before the application configured it), it is replaced.
   *
   * @throws IllegalStateException if the default client is already running with a valid
   *     configuration
   */
  public static ExceptionlessClient startup(Consumer<ExceptionlessConfiguration> configure) {
    synchronized (LOCK) {
      ExceptionlessClient client = getDefault();
      if (client.isStarted()) {
        if (client.getConfiguration().isEnabled()) {
          throw new IllegalStateException(
              "The default Exceptionless client has already started; configure it before using it");
        }
        client.close();
        client = new ExceptionlessClient(ExceptionlessConfiguration.fromEnvironment());
        defaultClient = client;
      }
      configure.accept(client.getConfiguration());
      return client.startup();
    }
  }

  /** Closes the default client, sending queued events. A new one is created on next use. */
  public static void shutdown() {
    ExceptionlessClient client;
    synchronized (LOCK) {
      client = defaultClient;
      defaultClient = null;
    }
    if (client != null) {
      client.close();
    }
  }

  /** Sends queued events and waits up to {@code timeout}. */
  public static boolean flush(Duration timeout) {
    return getDefault().flush(timeout);
  }

  public static ExceptionlessConfiguration getConfiguration() {
    return getDefault().getConfiguration();
  }

  public static EventBuilder createEvent() {
    return getDefault().createEvent();
  }

  public static EventBuilder createException(Throwable exception) {
    return getDefault().createException(exception);
  }

  public static EventBuilder createUnhandledException(
      Throwable exception, @Nullable String submissionMethod) {
    return getDefault().createUnhandledException(exception, submissionMethod);
  }

  public static EventBuilder createLog(String message) {
    return getDefault().createLog(message);
  }

  public static EventBuilder createLog(@Nullable String source, String message) {
    return getDefault().createLog(source, message);
  }

  public static EventBuilder createLog(
      @Nullable String source, String message, @Nullable LogLevel level) {
    return getDefault().createLog(source, message, level);
  }

  public static EventBuilder createFeatureUsage(String feature) {
    return getDefault().createFeatureUsage(feature);
  }

  public static EventBuilder createNotFound(String resource) {
    return getDefault().createNotFound(resource);
  }

  public static void submitException(Throwable exception) {
    getDefault().submitException(exception);
  }

  public static void submitUnhandledException(
      Throwable exception, @Nullable String submissionMethod) {
    getDefault().submitUnhandledException(exception, submissionMethod);
  }

  public static void submitLog(String message) {
    getDefault().submitLog(message);
  }

  public static void submitLog(@Nullable String source, String message) {
    getDefault().submitLog(source, message);
  }

  public static void submitLog(@Nullable String source, String message, @Nullable LogLevel level) {
    getDefault().submitLog(source, message, level);
  }

  public static void submitFeatureUsage(String feature) {
    getDefault().submitFeatureUsage(feature);
  }

  public static void submitNotFound(String resource) {
    getDefault().submitNotFound(resource);
  }

  public static @Nullable String getLastReferenceId() {
    return getDefault().getLastReferenceId();
  }
}
