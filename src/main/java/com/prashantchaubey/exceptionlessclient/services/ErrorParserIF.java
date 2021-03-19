package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.Error;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginContext;

public interface ErrorParserIF {
    Error parse(EventPluginContext context, Exception exception);
}
