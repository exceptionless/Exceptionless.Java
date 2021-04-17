package com.exceptionless.exceptionlessclient.exceptions;

public class SettingsClientException extends RuntimeException{
    public SettingsClientException(Throwable cause) {
        super(cause);
    }

    public SettingsClientException(String message) {
        super(message);
    }
}
