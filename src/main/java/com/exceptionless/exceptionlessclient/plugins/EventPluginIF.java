package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;

public interface EventPluginIF {
  int getPriority();

  default String getName() {
    return getClass().getName();
  }

  void run(EventPluginContext eventPluginContext, Configuration configuration);
}
