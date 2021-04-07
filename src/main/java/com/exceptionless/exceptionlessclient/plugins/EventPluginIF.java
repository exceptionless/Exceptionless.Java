package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;

public interface EventPluginIF {
  int getPriority();

  default String getName() {
    return getClass().getName();
  }

  void run(EventPluginContext eventPluginContext, ConfigurationManager configurationManager);
}
