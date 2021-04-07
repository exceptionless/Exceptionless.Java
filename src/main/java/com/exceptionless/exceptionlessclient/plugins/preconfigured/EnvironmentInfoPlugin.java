package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.services.EnvironmentInfoGetArgs;
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
