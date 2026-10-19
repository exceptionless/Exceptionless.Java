package com.exceptionless.plugins;

import com.exceptionless.internal.ModuleCollector;
import com.exceptionless.models.Event;
import com.exceptionless.models.ModuleInfo;
import org.jspecify.annotations.Nullable;

/**
 * Sets the application version ({@code @version}) from the configuration, falling back to the
 * {@code Implementation-Version} of the application's jar.
 */
public final class VersionPlugin implements EventPlugin {
  public static final int PRIORITY = 80;

  /** Lazily initialized holder: the version is looked up once, the first time it is needed. */
  private static final class Detected {
    static final @Nullable String VERSION = detect();
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    if (event.getVersion() != null) {
      return;
    }
    String version = context.getConfiguration().getVersion();
    if (version == null) {
      version = Detected.VERSION;
    }
    if (version != null) {
      event.setVersion(version);
    }
  }

  /** The version of the module containing the application's entry point, if it has one. */
  private static @Nullable String detect() {
    for (ModuleInfo module : ModuleCollector.getModules()) {
      if (module.entry() && module.version() != null) {
        return module.version();
      }
    }
    return null;
  }
}
