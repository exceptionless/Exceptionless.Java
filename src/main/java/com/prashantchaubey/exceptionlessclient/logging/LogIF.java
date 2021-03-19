package com.prashantchaubey.exceptionlessclient.logging;

public interface LogIF {
    String getTrace(String message);

    String getInfo(String message);

    String getWarn(String message);

    String getError(String message);
}
