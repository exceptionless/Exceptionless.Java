package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.PluginContext;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;

public interface RequestInfoCollectorIF {
    RequestInfo getRequestInfo(PluginContext context);
}
