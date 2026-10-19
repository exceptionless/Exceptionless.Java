package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.internal.Log;
import com.exceptionless.models.ErrorInfo;
import com.exceptionless.models.Event;
import com.exceptionless.settings.ClientSettings;
import java.util.function.Predicate;

/**
 * Discards events using the project settings and custom exclusions:
 *
 * <ul>
 *   <li>log events below the {@code @@log:<source>} minimum level
 *   <li>errors whose type (or the type of any cause) has {@code @@error:<type>} set to false
 *   <li>any other event whose {@code @@<type>:<source>} setting is false
 *   <li>events matching {@link ExceptionlessConfiguration#addEventExclusion}
 * </ul>
 */
public final class EventExclusionPlugin implements EventPlugin {
  public static final int PRIORITY = 40;
  private static final Log LOG = Log.get(EventExclusionPlugin.class);

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    ExceptionlessConfiguration config = context.getConfiguration();
    Event event = context.getEvent();

    for (Predicate<Event> exclusion : config.getEventExclusions()) {
      try {
        if (exclusion.test(event)) {
          LOG.debug("Cancelling event: excluded by a custom event exclusion");
          context.cancel();
          return;
        }
      } catch (RuntimeException e) {
        LOG.warn("Event exclusion callback failed", e);
      }
    }

    ClientSettings settings = config.getSettings();
    String type = event.getType();
    if (event.isLog()) {
      LogLevel level = LogLevel.parse(event.getLevel());
      if (level == null) {
        return;
      }
      LogLevel minimum = settings.getMinLogLevel(event.getSource());
      if (minimum == null) {
        minimum = config.getDefaultMinLogLevel();
      }
      if (minimum != null && (minimum == LogLevel.OFF || !level.isAtLeast(minimum))) {
        LogLevel min = minimum;
        LOG.debug(
            () ->
                "Cancelling "
                    + level
                    + " log event from "
                    + event.getSource()
                    + " (minimum "
                    + min
                    + ")");
        context.cancel();
      }
      return;
    }

    if (type != null && !settings.getTypeAndSourceSetting(type, event.getSource(), true)) {
      LOG.debug(
          () -> "Cancelling event from excluded type/source: " + type + " " + event.getSource());
      context.cancel();
      return;
    }

    ErrorInfo error = event.getError();
    while (error != null) {
      if (!settings.getTypeAndSourceSetting("error", error.getType(), true)) {
        String errorType = error.getType();
        LOG.debug(() -> "Cancelling error from excluded exception type " + errorType);
        context.cancel();
        return;
      }
      error = error.getInner();
    }
  }
}
