package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
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
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    for (String tag : configurationManager.getDefaultTags()) {
      event.addTags(tag);
    }

    event.addData(configurationManager.getDefaultData(), configurationManager.getDataExclusions());
  }
}
