package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.error.Error;

public interface ErrorParserIF {
    Error parse(Exception exception);
}
