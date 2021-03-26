package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.error.Error;

public interface ErrorParserIF {
    Error parse(Exception exception);
}
