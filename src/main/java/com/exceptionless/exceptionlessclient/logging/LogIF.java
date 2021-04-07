package com.exceptionless.exceptionlessclient.logging;

public interface LogIF {
    void trace(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Exception e);
}
