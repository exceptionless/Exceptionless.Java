package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import java.util.function.Consumer;

/**
 * A step in the event pipeline. Plugins run in ascending {@link #getPriority() priority} order on
 * the thread that submitted the event, so they can read thread-local state (MDC, the current
 * request...). They must be fast and must not block.
 */
public interface EventPlugin {
  /**
   * Creates a plugin from a lambda.
   *
   * <pre>{@code
   * config.addPlugin(EventPlugin.of("AddTenant", 100, ctx -> ctx.getEvent().addTags(tenant())));
   * }</pre>
   *
   * @param name a unique name
   * @param priority lower values run first
   * @param action processes each event
   */
  static EventPlugin of(String name, int priority, Consumer<EventPluginContext> action) {
    return new LambdaEventPlugin(name, priority, action);
  }

  /** A unique name; adding a plugin with the same name replaces the existing one. */
  default String getName() {
    return getClass().getSimpleName();
  }

  /** Lower values run first. The built-in plugins use values between 10 and 1010. */
  default int getPriority() {
    return 0;
  }

  /** Processes an event. Call {@link EventPluginContext#cancel()} to discard it. */
  void run(EventPluginContext context);

  /** Called once before the plugin first runs. */
  default void startup(ExceptionlessClient client) {}

  /** Called when the client is closed, so buffered work can be flushed. */
  default void suspend(ExceptionlessClient client) {}
}
