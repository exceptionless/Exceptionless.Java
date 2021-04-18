package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.services.EnvironmentInfoGetArgs;
import lombok.Builder;

public class EnvironmentInfoPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 80;

  @Builder
  public EnvironmentInfoPlugin() {}

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    if (event.getEnvironmentInfo().isEmpty()) {
      event.addEnvironmentInfo(
          configurationManager
              .getEnvironmentInfoCollector()
              .getEnvironmentInfo(
                  EnvironmentInfoGetArgs.builder()
                      .includeIpAddress(
                          configurationManager.getPrivateInformationInclusions().getIpAddress())
                      .includeMachineName(
                          configurationManager.getPrivateInformationInclusions().getMachineName())
                      .build()));
    }
  }
}
