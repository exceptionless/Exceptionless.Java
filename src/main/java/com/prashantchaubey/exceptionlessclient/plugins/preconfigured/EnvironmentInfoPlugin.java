package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfoGetArgs;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

public class EnvironmentInfoPlugin implements EventPluginIF {
  @Builder
  public EnvironmentInfoPlugin() {}

  @Override
  public int getPriority() {
    return 80;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    if (event.getData().get(EventPropertyKey.ENVIRONMENT.value()) == null) {
      event.addEnvironmentInfo(
          configurationManager
              .getEnvironmentInfoCollector()
              .getEnvironmentInfo(
                  EnvironmentInfoGetArgs.builder()
                      .includeIpAddress(true)
                      .includeMachineName(true)
                      .build()));
    }
  }
}
