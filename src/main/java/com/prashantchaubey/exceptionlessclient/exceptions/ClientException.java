package com.prashantchaubey.exceptionlessclient.exceptions;

public class ClientException extends RuntimeException {
  public ClientException(Throwable cause) {
    super(cause);
  }

  public ClientException(String message) {
    super(message);
  }
}
