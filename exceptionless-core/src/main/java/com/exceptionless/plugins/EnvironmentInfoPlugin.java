package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.EnvironmentCollector;
import com.exceptionless.models.Event;

/** Adds machine, OS and JVM information ({@code @environment}). */
public final class EnvironmentInfoPlugin implements EventPlugin {
  public static final int PRIORITY = 50;

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    if (event.getEnvironmentInfo() != null) {
      return;
    }
    ExceptionlessConfiguration config = context.getConfiguration();
    event.setEnvironmentInfo(
        EnvironmentCollector.collect(
            config.isIncludeMachineName(),
            config.isIncludeIpAddress(),
            context.getClient().getInstallId()));
  }
}
