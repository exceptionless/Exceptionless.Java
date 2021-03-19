package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.RequestInfo;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginContext;

public interface RequestInfoCollectorIF {
    RequestInfo getRequestInfo(EventPluginContext context);
}
