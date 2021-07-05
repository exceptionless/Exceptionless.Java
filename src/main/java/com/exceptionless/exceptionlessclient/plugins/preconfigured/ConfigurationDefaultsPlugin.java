package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

public class ConfigurationDefaultsPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 10;

  @Builder
  public ConfigurationDefaultsPlugin() {}

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, Configuration configuration) {
    Event event = eventPluginContext.getEvent();
    for (String tag : configuration.getDefaultTags()) {
      event.addTags(tag);
    }

    event.addData(configuration.getDefaultData(), configuration.getDataExclusions());
  }
}
