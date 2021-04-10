package com.exceptionless.exceptionlessclient.logging;

public interface LogCapturerIF {
    void trace(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Exception e);
}
