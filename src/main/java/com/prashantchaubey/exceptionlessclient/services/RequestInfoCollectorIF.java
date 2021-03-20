package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginContext;

public interface RequestInfoCollectorIF {
    RequestInfo getRequestInfo(EventPluginContext context);
}
