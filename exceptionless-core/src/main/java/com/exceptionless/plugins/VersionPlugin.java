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

  private volatile @Nullable String detected;
  private volatile boolean detectionAttempted;

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
      version = detect();
    }
    if (version != null) {
      event.setVersion(version);
    }
  }

  private @Nullable String detect() {
    if (!detectionAttempted) {
      for (ModuleInfo module : ModuleCollector.getModules()) {
        String version = module.version();
        if (module.entry() && version != null) {
          detected = version;
          break;
        }
      }
      detectionAttempted = true;
    }
    return detected;
  }
}
