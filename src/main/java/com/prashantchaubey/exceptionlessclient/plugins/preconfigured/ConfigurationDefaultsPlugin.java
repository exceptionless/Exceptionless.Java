package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ConfigurationDefaultsPlugin implements EventPluginIF {
  @Override
  public int getPriority() {
    return 10;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    for (String tag : configurationManager.getDefaultTags()) {
      if (event.getTags().contains(tag)) {
        continue;
      }
      event.addTags(tag);
    }

    event.addData(configurationManager.getDefaultData(), configurationManager.getDataExclusions());
  }
}
