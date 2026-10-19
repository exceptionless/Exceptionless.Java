package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.DataNormalizer;
import com.exceptionless.json.JsonSerializable;
import com.exceptionless.models.Event;
import java.util.Map;
import java.util.function.Predicate;

/** Adds the configured default tags and data to every event. */
public final class ConfigurationDefaultsPlugin implements EventPlugin {
  public static final int PRIORITY = 10;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    ExceptionlessConfiguration config = context.getConfiguration();
    Event event = context.getEvent();
    event.addTags(config.getDefaultTags());

    Map<String, Object> defaults = config.getDefaultData();
    if (defaults.isEmpty()) {
      return;
    }
    Predicate<String> excluded = config::isDataExcluded;
    for (Map.Entry<String, Object> entry : defaults.entrySet()) {
      String key = entry.getKey();
      if (event.getData().containsKey(key) || excluded.test(key)) {
        continue;
      }
      Object value = entry.getValue();
      if (!(value instanceof JsonSerializable)) {
        value = DataNormalizer.normalize(value, DataNormalizer.DEFAULT_MAX_DEPTH, excluded);
      }
      if (value != null) {
        event.getData().put(key, value);
      }
    }
  }
}
