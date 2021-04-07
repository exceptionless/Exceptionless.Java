package com.exceptionless.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;

public interface EventPluginIF {
  int getPriority();

  default String getName() {
    return getClass().getName();
  }

  void run(EventPluginContext eventPluginContext, ConfigurationManager configurationManager);
}
