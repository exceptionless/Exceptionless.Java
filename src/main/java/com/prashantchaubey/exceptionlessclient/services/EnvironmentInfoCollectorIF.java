package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.EnvironmentInfo;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginContext;

public interface EnvironmentInfoCollectorIF {
    EnvironmentInfo getEnvironmentInfo(EventPluginContext context);
}
